package com.loohp.imageframe.utils;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class FutureUtils {

    public static Future<Void> callSyncMethod(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return CompletableFuture.completedFuture(null);
        } else {
            return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, () -> {
                task.run();
                return null;
            });
        }
    }

    public static <T> Future<T> callSyncMethod(Callable<T> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        }
        return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, task);
    }

    public static <T> Future<T> callSyncMethod(Callable<T> task, Callable<T> retired, Entity entity) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        }
        return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, task);
    }

    public static <T> Future<T> callAsyncMethod(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static Runnable errorHandled(ThrowingRunnable runnable, Consumer<Throwable> errorHandler) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                errorHandler.accept(e);
            }
        };
    }

    public static <T> void applyWhenComplete(Future<T> future, Consumer<T> completionTask, Consumer<Throwable> errorHandler, boolean synced) {
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            try {
                T t = future.get();
                if (synced) {
                    Scheduler.runTask(ImageFrame.plugin, () -> completionTask.accept(t));
                } else {
                    completionTask.accept(t);
                }
            } catch (InterruptedException | ExecutionException e) {
                errorHandler.accept(e);
            }
        });
    }

    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Throwable;

    }
}
