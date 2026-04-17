package me.cookie.duel.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PaperSchedulerFacade implements SchedulerFacade {

    private final JavaPlugin plugin;
    private final Server server;
    private final boolean folia;

    public PaperSchedulerFacade(JavaPlugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.folia = "folia".equalsIgnoreCase(server.getName()) || server.getVersion().toLowerCase().contains("folia");
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public void runSync(Runnable runnable) {
        server.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    @Override
    public TaskHandle runLater(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0L) {
            runSync(runnable);
            return NoOpTaskHandle.INSTANCE;
        }

        ScheduledTask task = server.getGlobalRegionScheduler().runDelayed(plugin, ignored -> runnable.run(), delayTicks);
        return () -> task.cancel();
    }

    @Override
    public <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runSync(() -> completeFuture(future, supplier));
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.getAsyncScheduler().runNow(plugin, ignored -> completeFuture(future, supplier));
        return future;
    }

    @Override
    public void runAtLocation(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location must include a world.");
        }
        server.getRegionScheduler().execute(plugin, location, runnable);
    }

    @Override
    public <T> CompletableFuture<T> supplyAtLocation(Location location, Supplier<T> supplier) {
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Location must include a world."));
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        server.getRegionScheduler().run(plugin, location, ignored -> completeFuture(future, supplier));
        return future;
    }

    @Override
    public void runForEntity(Entity entity, Runnable runnable) {
        if (entity == null) {
            return;
        }

        entity.getScheduler().execute(plugin, runnable, () -> {
        }, 0L);
    }

    @Override
    public <T> CompletableFuture<T> supplyForEntity(Entity entity, Supplier<T> supplier) {
        if (entity == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Entity cannot be null."));
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        boolean scheduled = entity.getScheduler().execute(
                plugin,
                () -> completeFuture(future, supplier),
                () -> future.completeExceptionally(new IllegalStateException("Entity scheduler retired for " + entity.getUniqueId())),
                0L
        );
        if (!scheduled) {
            future.completeExceptionally(new IllegalStateException("Could not schedule entity task for " + entity.getUniqueId()));
        }
        return future;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        if (entity == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Entity cannot be null."));
        }
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Teleport location must include a world."));
        }

        return supplyForEntity(entity, () -> entity.teleportAsync(location)).thenCompose(result -> result);
    }

    @Override
    public boolean isOwnedByCurrentRegion(Location location, int chunkRadius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return server.isOwnedByCurrentRegion(location, Math.max(0, chunkRadius));
    }

    private <T> void completeFuture(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private enum NoOpTaskHandle implements TaskHandle {
        INSTANCE;

        @Override
        public void cancel() {
        }
    }
}
