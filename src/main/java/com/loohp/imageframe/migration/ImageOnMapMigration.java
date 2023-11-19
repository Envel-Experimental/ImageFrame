package com.loohp.imageframe.migration;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.NonUpdatableStaticImageMap;
import com.loohp.imageframe.utils.MapUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.simpleyaml.configuration.file.YamlFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ImageOnMapMigration implements ExternalPluginMigration {

    public static final String PLUGIN_NAME = "ImageOnMap";
    private static int globalMapIndex = 1;

    @Override
    public String externalPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean requirePlayer() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void migrate(UUID unused) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageOnMap must be disabled for migration to begin");
                    return;
                }

                File migrationMarker = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageOnMap/imageframe-migrated.bin");
                if (migrationMarker.exists()) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ImageFrame] ImageOnMap data already marked as migrated");
                    return;
                }

                File userFolder = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageOnMap/maps");
                if (!userFolder.exists() || !userFolder.isDirectory()) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageOnMap plugin data folder not found");
                    return;
                }

                World world = MapUtils.getMainWorld();
                File imageFolder = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageOnMap/images");

                File[] files = userFolder.listFiles();
                if (files == null) {
                    return;
                }

                Map<UUID, Integer> userMapIndex = new HashMap<>();
                List<CompletableFuture<Void>> migrationFutures = new ArrayList<>();

                for (File file : files) {
                    UUID owner;
                    try {
                        String fileNameWithoutExtension = file.getName().substring(0, file.getName().lastIndexOf("."));
                        owner = UUID.fromString(fileNameWithoutExtension);
                    } catch (IllegalArgumentException e) {
                        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to get UUID for file: " + file.getName());
                        continue;
                    }

                    int currentIndex = userMapIndex.getOrDefault(owner, 0);
                    userMapIndex.put(owner, currentIndex);

                    migrationFutures.add(CompletableFuture.runAsync(() -> migrateFile(file, imageFolder, owner, currentIndex)));
                }

                CompletableFuture<Void> allMigrations = CompletableFuture.allOf(migrationFutures.toArray(new CompletableFuture[0]));

                allMigrations.thenRun(() -> {
                    try {
                        migrationMarker.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] ImageOnMap migration complete!");
                });
            }

            private void migrateFile(File file, File imageFolder, UUID owner, int startIndex) {
                try {
                    YamlFile yaml = YamlFile.loadConfiguration(file);
                    List<Map<String, Object>> mapList = (List<Map<String, Object>>) yaml.getList("PlayerMapStore.mapList");
                    if (mapList == null) {
                        return;
                    }

                    int index = startIndex;

                    for (Map<String, Object> section : mapList) {
                        try {
                            String name = "Map-" + globalMapIndex++;
                            String iomId = (String) section.get("id");
                            String type = (String) section.get("type");
                            BufferedImage[] images;
                            List<Integer> mapIds;
                            int width;
                            int height;

                            if (type.equalsIgnoreCase("SINGLE")) {
                                int mapId = (int) section.get("mapID");
                                mapIds = Collections.singletonList(mapId);
                                images = new BufferedImage[]{ImageIO.read(new File(imageFolder, "map" + mapId + ".png"))};
                                width = 1;
                                height = 1;
                            } else if (type.equalsIgnoreCase("POSTER")) {
                                mapIds = new ArrayList<>((List<Integer>) section.get("mapsIDs"));
                                images = new BufferedImage[mapIds.size()];
                                width = (int) section.get("columns");
                                height = (int) section.get("rows");

                                for (int i = 0; i < images.length; i++) {
                                    images[i] = ImageIO.read(new File(imageFolder, "map" + mapIds.get(i) + ".png"));
                                }
                            } else {
                                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate " + name + " ImageOnMap user file " + file.getName() + ": Unknown type " + type);
                                continue;
                            }

                            NonUpdatableStaticImageMap imageMap;

                            if (ImageFrame.imageMapManager.getFromCreator(owner, name) == null) {
                                imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, name, images, mapIds, width, height, owner).get();
                            } else if (ImageFrame.imageMapManager.getFromCreator(owner, iomId) == null) {
                                imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, iomId, images, mapIds, width, height, owner).get();
                            } else {
                                imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, "ImageOnMap-" + iomId, images, mapIds, width, height, owner).get();
                            }

                            ImageFrame.imageMapManager.addMap(imageMap);
                            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Migrated ImageOnMap " + file.getName() + " to " + name + " of " + owner);
                        } catch (Exception e) {
                            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate ImageOnMap " + file.getName() + " of index " + index);
                            e.printStackTrace();
                        }

                        index++;
                    }
                } catch (IOException e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate ImageOnMap user file " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(ImageFrame.plugin);
    }
}