package me.cookie.duel.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface SchedulerFacade {

    boolean isFolia();

    void runSync(Runnable runnable);

    TaskHandle runLater(Runnable runnable, long delayTicks);

    <T> CompletableFuture<T> supplySync(Supplier<T> supplier);

    <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier);

    void runAtLocation(Location location, Runnable runnable);

    <T> CompletableFuture<T> supplyAtLocation(Location location, Supplier<T> supplier);

    void runForEntity(Entity entity, Runnable runnable);

    <T> CompletableFuture<T> supplyForEntity(Entity entity, Supplier<T> supplier);

    CompletableFuture<Boolean> teleport(Entity entity, Location location);

    boolean isOwnedByCurrentRegion(Location location, int chunkRadius);

    interface TaskHandle {
        void cancel();
    }
}
