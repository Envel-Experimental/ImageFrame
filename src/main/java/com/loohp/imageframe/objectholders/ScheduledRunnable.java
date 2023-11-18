package com.loohp.imageframe.objectholders;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public abstract class ScheduledRunnable implements Runnable {

    private Object task;

    public synchronized boolean isCancelled() {
        checkScheduled();
        return ((BukkitTask) task).isCancelled();
    }

    public synchronized void cancel() {
        checkScheduled();
        ((BukkitTask) task).cancel();
    }

    public synchronized Scheduler.ScheduledTask runTask(Plugin plugin, Entity entity) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTask(plugin, this));
    }

    public synchronized Scheduler.ScheduledTask runTaskLater(Plugin plugin, long delay, Entity entity) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskLater(plugin, this, delay));
    }

    public synchronized Scheduler.ScheduledTask runTaskTimer(Plugin plugin, long delay, long period, Entity entity) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskTimer(plugin, this, delay, period));
    }

    public synchronized Scheduler.ScheduledTask runTask(Plugin plugin, Location location) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTask(plugin, this));
    }

    public synchronized Scheduler.ScheduledTask runTaskLater(Plugin plugin, long delay, Location location) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskLater(plugin, this, delay));
    }

    public synchronized Scheduler.ScheduledTask runTaskTimer(Plugin plugin, long delay, long period, Location location) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskTimer(plugin, this, delay, period));
    }

    public synchronized Scheduler.ScheduledTask runTask(Plugin plugin) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTask(plugin, this));
    }

    public synchronized Scheduler.ScheduledTask runTaskLater(Plugin plugin, long delay) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskLater(plugin, this, delay));
    }

    public synchronized Scheduler.ScheduledTask runTaskTimer(Plugin plugin, long delay, long period) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskTimer(plugin, this, delay, period));
    }

    public synchronized Scheduler.ScheduledTask runTaskAsynchronously(Plugin plugin) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskAsynchronously(plugin, this));
    }

    public synchronized Scheduler.ScheduledTask runTaskLaterAsynchronously(Plugin plugin, long delay) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, delay));
    }

    public synchronized Scheduler.ScheduledTask runTaskTimerAsynchronously(Plugin plugin, long delay, long period) {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, delay, period));
    }

    private void checkScheduled() {
        if (task == null) {
            throw new IllegalStateException("Not scheduled yet");
        }
    }

    private void checkNotYetScheduled() {
        if (task != null) {
            throw new IllegalStateException("Already scheduled as " + task);
        }
    }

    private Scheduler.ScheduledTask setupTask(Object task) {
        this.task = task;
        return new Scheduler.ScheduledTask(task);
    }
}
