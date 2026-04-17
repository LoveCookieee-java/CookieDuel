package me.cookie.duel.duel.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import me.cookie.duel.scheduler.SchedulerFacade;

public final class SnapshotService {

    private static final Attribute MAX_HEALTH_ATTRIBUTE = resolveMaxHealthAttribute();

    private final SchedulerFacade schedulerFacade;
    private final Map<UUID, PendingRestore> pendingRestores = new ConcurrentHashMap<>();

    public SnapshotService(SchedulerFacade schedulerFacade) {
        this.schedulerFacade = schedulerFacade;
    }

    public CompletableFuture<PlayerSnapshot> capture(Player player) {
        if (player == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Player must not be null."));
        }

        return schedulerFacade.supplyForEntity(player, () -> new PlayerSnapshot(
                cloneItems(player.getInventory().getContents()),
                cloneItems(player.getInventory().getArmorContents()),
                cloneItem(player.getInventory().getItemInOffHand()),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getExp(),
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getLocation().clone()
        ));
    }

    public CompletableFuture<Void> restoreOrQueue(UUID playerId,
                                                  PlayerSnapshot snapshot,
                                                  Location targetLocation,
                                                  boolean restoreInventory) {
        Player player = Bukkit.getPlayer(playerId);
        if (snapshot == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (player == null) {
            pendingRestores.put(playerId, new PendingRestore(snapshot, targetLocation, restoreInventory));
            return CompletableFuture.completedFuture(null);
        }

        Location destination = targetLocation == null ? snapshot.returnLocation() : targetLocation;
        return schedulerFacade.supplyForEntity(player, () -> {
            applyState(player, snapshot, restoreInventory);
            return null;
        }).thenCompose(ignored -> schedulerFacade.teleport(player, destination))
                .thenCompose(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.failedFuture(new IllegalStateException("Failed to restore player teleport for " + playerId));
                });
    }

    public void applyPendingRestore(Player player) {
        PendingRestore pendingRestore = pendingRestores.remove(player.getUniqueId());
        if (pendingRestore == null) {
            return;
        }

        Location destination = pendingRestore.targetLocation() == null
                ? pendingRestore.snapshot().returnLocation()
                : pendingRestore.targetLocation();
        schedulerFacade.supplyForEntity(player, () -> {
            applyState(player, pendingRestore.snapshot(), pendingRestore.restoreInventory());
            return null;
        }).thenCompose(ignored -> schedulerFacade.teleport(player, destination));
    }

    private void applyState(Player player, PlayerSnapshot snapshot, boolean restoreInventory) {
        if (restoreInventory) {
            player.getInventory().setContents(cloneItems(snapshot.inventoryContents()));
            player.getInventory().setArmorContents(cloneItems(snapshot.armorContents()));
            player.getInventory().setItemInOffHand(cloneItem(snapshot.offHand()));
            player.updateInventory();
        }

        AttributeInstance maxHealthInstance = MAX_HEALTH_ATTRIBUTE == null
                ? null
                : player.getAttribute(MAX_HEALTH_ATTRIBUTE);
        double maxHealth = maxHealthInstance == null
                ? 20.0D
                : maxHealthInstance.getValue();

        player.setHealth(Math.min(snapshot.health(), maxHealth));
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.experience());
        player.setGameMode(snapshot.gameMode());
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying());
        player.setFallDistance(0.0F);
        player.setFireTicks(0);
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] clone = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clone[i] = cloneItem(items[i]);
        }
        return clone;
    }

    private ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

    private static Attribute resolveMaxHealthAttribute() {
        return readAttributeField("MAX_HEALTH", readAttributeField("GENERIC_MAX_HEALTH", null));
    }

    private static Attribute readAttributeField(String fieldName, Attribute fallback) {
        try {
            Field field = Attribute.class.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Attribute attribute ? attribute : fallback;
        } catch (ReflectiveOperationException exception) {
            return fallback;
        }
    }

    private record PendingRestore(PlayerSnapshot snapshot, Location targetLocation, boolean restoreInventory) {
    }
}
