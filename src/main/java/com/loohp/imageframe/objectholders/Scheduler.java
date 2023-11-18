package com.loohp.imageframe.objectholders;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public class Scheduler {

    public static ScheduledTask runTask(Plugin plugin, Runnable task, Entity entity) {
        return new ScheduledTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delay, Entity entity) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period, Entity entity) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    public static ScheduledTask runTask(Plugin plugin, Runnable task, Location location) {
        return new ScheduledTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delay, Location location) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period, Location location) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    public static ScheduledTask runTask(Plugin plugin, Runnable task) {
        return new ScheduledTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    public static ScheduledTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    public static ScheduledTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay));
    }

    public static ScheduledTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        return new ScheduledTask(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period));
    }

    public static class ScheduledTask {

        private final Object task;

        public ScheduledTask(Object task) {
            this.task = task;
        }

        public boolean isCancelled() {
            return ((BukkitTask) task).isCancelled();
        }

        public void cancel() {
            ((BukkitTask) task).cancel();
        }

        public Plugin getOwner() {
            return ((BukkitTask) task).getOwner();
        }

    }

}
