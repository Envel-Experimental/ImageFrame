/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.imageframe.listeners;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.CombinedMapItemHandler;
import com.loohp.imageframe.objectholders.Scheduler;
import com.loohp.imageframe.utils.MapUtils;
import io.github.bananapuncher714.nbteditor.NBTEditor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.Collection;
import java.util.function.IntFunction;


public class Events implements Listener {

    private boolean isCombinedMaps(ItemStack itemStack) {
        return itemStack != null && itemStack.getType().equals(Material.PAPER) && NBTEditor.contains(itemStack, CombinedMapItemHandler.COMBINED_MAP_KEY);
    }

    private void handleDeletedMap(ItemStack itemStack) {
        Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
            MapView mapView = MapUtils.getItemMapView(itemStack);
            if (mapView != null && ImageFrame.imageMapManager.isMapDeleted(mapView) && !ImageFrame.exemptMapIdsFromDeletion.satisfies(mapView.getId())) {
                Bukkit.getScheduler().runTask(ImageFrame.plugin, () -> itemStack.setType(Material.MAP));
            }
        });
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        MapView currentMapView = MapUtils.getItemMapView(currentItem);
        if (currentMapView != null) {
            handleDeletedMap(currentItem);
        }

        boolean isClickingTop = event.getView().getTopInventory().equals(event.getClickedInventory());
        boolean isClickingBottom = event.getView().getBottomInventory().equals(event.getClickedInventory());

        Inventory inventory = event.getView().getTopInventory();
        if (inventory.getType().equals(InventoryType.WORKBENCH)) {
            if ((isClickingTop && isCombinedMaps(event.getCursor()))
                    || (isClickingBottom && event.isShiftClick() && isCombinedMaps(event.getCurrentItem()))
                    || (isClickingTop && event.getHotbarButton() != -1 && isCombinedMaps(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())))
                    || (isClickingTop && event.getClick().equals(ClickType.SWAP_OFFHAND) && isCombinedMaps(event.getWhoClicked().getEquipment().getItemInOffHand()))
                    || containsCombinedMaps(i -> event.getView().getItem(i), 10)) {
                event.setResult(Event.Result.DENY);
            } else if (event.getRawSlot() == 0) {
                ItemStack map = event.getView().getItem(5);
                MapView mapView = MapUtils.getItemMapView(map);
                if (mapView == null || ImageFrame.imageMapManager.getFromMapView(mapView) == null) {
                    return;
                }
                int count = 0;
                for (int i = 1; i <= 9; i++) {
                    if (i == 5) {
                        continue;
                    }
                    ItemStack itemStack = event.getView().getItem(i);
                    if (itemStack != null && itemStack.getType().equals(Material.PAPER)) {
                        count++;
                    }
                }
                if (count >= 8) {
                    event.setResult(Event.Result.DENY);
                }
            }
        } else if (inventory.getType().equals(InventoryType.CARTOGRAPHY)) {
            if ((isClickingTop && isCombinedMaps(event.getCursor()))
                    || (isClickingBottom && event.isShiftClick() && isCombinedMaps(event.getCurrentItem()))
                    || (isClickingTop && event.getHotbarButton() != -1 && isCombinedMaps(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())))
                    || (isClickingTop && event.getClick().equals(ClickType.SWAP_OFFHAND) && isCombinedMaps(event.getWhoClicked().getEquipment().getItemInOffHand()))
                    || containsCombinedMaps(i -> event.getView().getItem(i), 3)) {
                event.setResult(Event.Result.DENY);
            } else if (event.getRawSlot() == 2) {
                ItemStack map = event.getView().getItem(0);
                MapView mapView = MapUtils.getItemMapView(map);
                if (mapView == null || ImageFrame.imageMapManager.getFromMapView(mapView) == null) {
                    return;
                }
                ItemStack item = event.getView().getItem(1);
                if (item != null && (item.getType().equals(Material.PAPER) || item.getType().equals(Material.GLASS_PANE))) {
                    event.setResult(Event.Result.DENY);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        int size = inventory.getSize();
        if (inventory.getType().equals(InventoryType.WORKBENCH) || inventory.getType().equals(InventoryType.CARTOGRAPHY)) {
            if (containsCombinedMaps(event.getNewItems().values()) && event.getNewItems().keySet().stream().anyMatch(i -> i < size)) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    private boolean containsCombinedMaps(IntFunction<ItemStack> slotAccess, int size) {
        for (int i = 0; i < size; i++) {
            if (isCombinedMaps(slotAccess.apply(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCombinedMaps(Collection<ItemStack> itemStacks) {
        return itemStacks.stream().anyMatch(this::isCombinedMaps);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwitchSlot(PlayerItemHeldEvent event) {
        ItemStack currentItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (currentItem != null) {
            handleDeletedMap(currentItem);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().equals(Action.PHYSICAL)) {
            return;
        }
        EntityEquipment equipment = event.getPlayer().getEquipment();
        EquipmentSlot hand = event.getHand();
        ItemStack currentItem = equipment.getItem(hand);
        if (currentItem != null) {
            handleDeletedMap(currentItem);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        EntityEquipment equipment = event.getPlayer().getEquipment();
        EquipmentSlot hand = event.getHand();
        ItemStack currentItem = equipment.getItem(hand);
        if (currentItem != null) {
            handleDeletedMap(currentItem);
        }

        Entity entity = event.getRightClicked();
        if (entity instanceof ItemFrame) {
            ItemFrame itemFrame = (ItemFrame) entity;
            handleDeletedMap(itemFrame.getItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ItemFrame) {
            ItemFrame itemFrame = (ItemFrame) entity;
            handleDeletedMap(itemFrame.getItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        ItemStack currentItem = item.getItemStack();
        handleDeletedMap(currentItem);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ItemFrame) {
                ItemFrame itemFrame = (ItemFrame) entity;
                handleDeletedMap(itemFrame.getItem());
            }
        }
    }
}