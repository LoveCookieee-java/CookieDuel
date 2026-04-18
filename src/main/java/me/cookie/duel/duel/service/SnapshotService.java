package me.cookie.duel.duel.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import me.cookie.duel.scheduler.SchedulerFacade;

public final class SnapshotService {

    private final SchedulerFacade schedulerFacade;
    private final Map<UUID, Location> pendingReturns = new ConcurrentHashMap<>();

    public SnapshotService(SchedulerFacade schedulerFacade) {
        this.schedulerFacade = schedulerFacade;
    }

    public CompletableFuture<PlayerSnapshot> capture(Player player) {
        if (player == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Player cannot be null."));
        }

        return schedulerFacade.supplyForEntity(player, () -> new PlayerSnapshot(player.getLocation().clone()));
    }

    public CompletableFuture<Void> returnOrQueue(UUID playerId,
                                                 PlayerSnapshot snapshot,
                                                 Location targetLocation) {
        Location destination = targetLocation != null
                ? targetLocation.clone()
                : snapshot == null || snapshot.returnLocation() == null
                ? null
                : snapshot.returnLocation().clone();
        if (destination == null) {
            return CompletableFuture.completedFuture(null);
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            pendingReturns.put(playerId, destination);
            return CompletableFuture.completedFuture(null);
        }

        return schedulerFacade.teleport(player, destination).thenCompose(success -> Boolean.TRUE.equals(success)
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new IllegalStateException("Could not return player " + playerId + " to a safe location.")));
    }

    public void applyPendingReturn(Player player) {
        Location destination = pendingReturns.remove(player.getUniqueId());
        if (destination == null) {
            return;
        }

        schedulerFacade.teleport(player, destination);
    }
}
