package com.loohp.imageframe.objectholders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.FileUtils;
import com.loohp.imageframe.utils.MapUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ImageMapManager implements AutoCloseable {

    @SuppressWarnings("deprecation")
    public static byte WHITE_PIXEL = MapPalette.matchColor(255, 255, 255);
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    public static final int FAKE_MAP_ID_START_RANGE = Integer.MAX_VALUE / 4 * 3;

    private static final AtomicInteger FAKE_MAP_ID_COUNTER = new AtomicInteger(FAKE_MAP_ID_START_RANGE);

    public static int getNextFakeMapId() {
        return FAKE_MAP_ID_COUNTER.getAndUpdate(i -> i < FAKE_MAP_ID_START_RANGE ? FAKE_MAP_ID_START_RANGE : i + 1);
    }

    private final Map<Integer, ImageMap> maps;
    private final Map<MapView, ImageMap> mapsByView;
    private final AtomicInteger mapIndexCounter;
    private final File dataFolder;
    private final AtomicInteger tickCounter;
    private final Scheduler.ScheduledTask task;
    private final List<ImageMapRenderEventListener> renderEventListeners;
    private final Set<Integer> deletedMapIds;

    public ImageMapManager(File dataFolder) {
        this.maps = new ConcurrentHashMap<>();
        this.mapsByView = new ConcurrentHashMap<>();
        this.mapIndexCounter = new AtomicInteger(0);
        this.dataFolder = dataFolder;
        this.tickCounter = new AtomicInteger(0);
        this.renderEventListeners = new CopyOnWriteArrayList<>();
        this.deletedMapIds = ConcurrentHashMap.newKeySet();
        this.task = Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, tickCounter::incrementAndGet, 0, 20);
    }

    public File getDataFolder() {
        return dataFolder;
    }

    protected int getCurrentAnimationTick() {
        return tickCounter.get();
    }

    @Override
    public void close() {
        saveDeletedMaps();
        task.cancel();
    }

    public void appendRenderEventListener(ImageMapRenderEventListener listener) {
        renderEventListeners.add(listener);
    }

    public void prependRenderEventListener(ImageMapRenderEventListener listener) {
        renderEventListeners.add(0, listener);
    }

    public void removeRenderEventListener(ImageMapRenderEventListener listener) {
        renderEventListeners.remove(listener);
    }

    protected void callRenderEventListener(ImageMapManager manager, ImageMap imageMap, MapView map, Player player, MutablePair<byte[], Collection<MapCursor>> renderData) {
        renderEventListeners.forEach(each -> each.accept(manager, imageMap, map, player, renderData));
    }

    public synchronized void addMap(ImageMap map) throws Exception {
        if (map.getManager() != this) {
            throw new IllegalArgumentException("ImageMap's manager is not set to this");
        }
        if (getFromCreator(map.getCreator(), map.getName()) != null) {
            throw new IllegalArgumentException("Duplicated map name for this creator");
        }
        int originalImageIndex = map.getImageIndex();
        if (originalImageIndex < 0) {
            map.imageIndex = mapIndexCounter.getAndIncrement();
        } else {
            mapIndexCounter.updateAndGet(i -> Math.max(originalImageIndex + 1, i));
        }
        maps.put(map.getImageIndex(), map);
        for (MapView mapView : map.getMapViews()) {
            mapsByView.put(mapView, map);
        }
        try {
            map.save();
        } catch (Throwable e) {
            maps.remove(originalImageIndex);
            for (MapView mapView : map.getMapViews()) {
                mapsByView.remove(mapView);
            }
            throw e;
        }
    }

    public boolean hasMap(int imageIndex) {
        return maps.containsKey(imageIndex);
    }

    public Collection<ImageMap> getMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public ImageMap getFromMapId(int id) {
        return getFromMapView(Bukkit.getMap(id));
    }

    public ImageMap getFromImageId(int imageId) {
        return maps.get(imageId);
    }

    public ImageMap getFromMapView(MapView mapView) {
        return mapsByView.get(mapView);
    }

    public Set<ImageMap> getFromCreator(UUID uuid) {
        return maps.values().stream().filter(each -> each.getCreator().equals(uuid)).collect(Collectors.toSet());
    }

    public List<ImageMap> getFromCreator(UUID uuid, Comparator<ImageMap> order) {
        return maps.values().stream().filter(each -> each.getCreator().equals(uuid)).sorted(order).collect(Collectors.toList());
    }

    public ImageMap getFromCreator(UUID uuid, String name) {
        return maps.values().stream().filter(each -> each.getCreator().equals(uuid) && each.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public Set<UUID> getCreators() {
        return maps.values().stream().map(each -> each.getCreator()).collect(Collectors.toSet());
    }

    public boolean deleteMap(int imageIndex) {
        ImageMap imageMap = maps.remove(imageIndex);
        if (imageMap == null) {
            return false;
        }
        List<MapView> mapViews = imageMap.getMapViews();
        for (MapView mapView : mapViews) {
            mapsByView.remove(mapView);
        }
        if (imageMap.trackDeletedMaps()) {
            mapViews.forEach(each -> deletedMapIds.add(each.getId()));
        }
        imageMap.markInvalid();
        dataFolder.mkdirs();
        File folder = new File(dataFolder, String.valueOf(imageIndex));
        if (folder.exists() && folder.isDirectory()) {
            FileUtils.removeFolderRecursively(folder);
        }
        imageMap.stop();
        saveDeletedMaps();
        Scheduler.runTask(ImageFrame.plugin, () -> {
            mapViews.forEach(each -> {
                if (each.getRenderers().isEmpty()) {
                    each.addRenderer(DeletedMapRenderer.INSTANCE);
                }
            });
        });
        return true;
    }

    public boolean isMapDeleted(int mapId) {
        return deletedMapIds.contains(mapId);
    }

    public boolean isMapDeleted(MapView mapView) {
        return isMapDeleted(mapView.getId());
    }

    public void loadMapsAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
            List<CompletableFuture<Void>> mapLoadFutures = new ArrayList<>();

            clearMaps();

            dataFolder.mkdirs();
            File[] files = dataFolder.listFiles();

            if (files == null) {
                return;
            }

            Arrays.sort(files, FileUtils.BY_NUMBER_THAN_STRING);

            int batchSize = 10;

            for (int i = 0; i < files.length; i += batchSize) {
                List<File> batch = Arrays.asList(files).subList(i, Math.min(i + batchSize, files.length));
                CompletableFuture<Void> batchLoadFuture = CompletableFuture.allOf(batch.stream()
                        .map(this::loadImageMapAsync)
                        .toArray(CompletableFuture[]::new));
                mapLoadFutures.add(batchLoadFuture);
            }

            CompletableFuture<Void> allMapLoadFuture = CompletableFuture.allOf(mapLoadFutures.toArray(new CompletableFuture[0]));

            allMapLoadFuture.thenRun(() -> {
                Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Data loading completed! Loaded " + maps.size() + " ImageMaps!");
            });
        });
    }

    private CompletableFuture<Void> loadImageMapAsync(File file) {
        return CompletableFuture.runAsync(() -> {
            try {
                ImageMap imageMap = ImageMap.load(this, file).get();
                addMap(imageMap);
            } catch (Throwable e) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMap data in " + file.getAbsolutePath());
            }
        });
    }

    private void loadDeletedMapsBinary(File file) {
        try (DataInputStream dataInputStream = new DataInputStream(Files.newInputStream(file.toPath()))) {
            while (true) {
                try {
                    deletedMapIds.add(dataInputStream.readInt());
                } catch (EOFException ignore) {
                    break;
                }
            }
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMapManager data in " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }


    private void loadDeletedMapsJson(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            JsonArray deletedMapIdsArray = json.get("mapids").getAsJsonArray();
            deletedMapIdsArray.forEach(element -> deletedMapIds.add(element.getAsInt()));
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMapManager data in " + file.getAbsolutePath());
        }
        saveDeletedMaps();
    }

    private void backupAndDeleteLegacyFile(File file) {
        try {
            Files.move(file.toPath(), new File(dataFolder, "deletedMaps.json.bak").toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void clearMaps() {
        maps.clear();
        mapsByView.clear();
        deletedMapIds.clear();
    }

    public synchronized void saveDeletedMaps() {
        File file = new File(dataFolder, "deletedMaps.bin");
        try (DataOutputStream dataOutputStream = new DataOutputStream(Files.newOutputStream(file.toPath()))) {
            for (int deletedMapId : deletedMapIds) {
                dataOutputStream.writeInt(deletedMapId);
            }
            dataOutputStream.flush();
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to save ImageMapManager data in " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public void sendAllMaps(Collection<? extends Player> players) {
        maps.values().forEach(m -> sendAllMaps(players));
    }

    public static class DeletedMapRenderer extends MapRenderer {

        public static final DeletedMapRenderer INSTANCE = new DeletedMapRenderer();

        private DeletedMapRenderer() {}

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            List<MapRenderer> mapRenderers = map.getRenderers();
            if (mapRenderers.size() != 1 || mapRenderers.get(0) != this) {
                Scheduler.runTaskLater(ImageFrame.plugin, () -> map.removeRenderer(this), 1);
                return;
            }
            for (int y = 0; y < MapUtils.MAP_WIDTH; y++) {
                for (int x = 0; x < MapUtils.MAP_WIDTH; x++) {
                    canvas.setPixel(x, y, WHITE_PIXEL);
                }
            }
        }
    }

}
