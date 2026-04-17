package me.cookie.duel.duel.teleport.wild;

import me.cookie.duel.config.model.WildBlacklistSettings;
import me.cookie.duel.config.model.WildLocationSettings;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class WildLocationService {

    private final WildLocationValidator validator;

    public WildLocationService(WildLocationValidator validator) {
        this.validator = validator;
    }

    public CompletableFuture<Optional<WildSpawnPair>> findSpawnPair(SchedulerFacade schedulerFacade,
                                                                    World world,
                                                                    WildLocationSettings settings,
                                                                    WildBlacklistSettings blacklistSettings,
                                                                    Set<UUID> ignoredPlayers) {
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return searchAttempt(schedulerFacade, world, settings, blacklistSettings, ignoredPlayers, 0);
    }

    public Location nextRandomCenter(World world, WildLocationSettings settings) {
        return settings.useRandomSafeLocation()
                ? randomCenter(world, settings)
                : world.getSpawnLocation().clone().add(0.5D, 0.0D, 0.5D);
    }

    public int requiredOwnershipRadiusChunks(WildLocationSettings settings) {
        double halfDistance = settings.spawnDistance() / 2.0D;
        int localReach = Math.max(
                Math.max(settings.localTerrainSampleRadius(), settings.edgeCheckRadius()),
                Math.max(settings.nearbyHazardCheckRadius(), settings.nearbyObstructionCheckRadius())
        );
        double totalReach = halfDistance + localReach + 2.0D;
        return Math.max(0, (int) Math.ceil(totalReach / 16.0D));
    }

    private CompletableFuture<Optional<WildSpawnPair>> searchAttempt(SchedulerFacade schedulerFacade,
                                                                     World world,
                                                                     WildLocationSettings settings,
                                                                     WildBlacklistSettings blacklistSettings,
                                                                     Set<UUID> ignoredPlayers,
                                                                     int attempt) {
        if (attempt >= settings.maxAttempts()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        Location center = nextRandomCenter(world, settings);
        return schedulerFacade.supplyAtLocation(center, () -> validatePairAtCenter(
                schedulerFacade,
                center,
                settings,
                blacklistSettings,
                ignoredPlayers
        )).thenCompose(result -> result.isPresent()
                ? CompletableFuture.completedFuture(result)
                : searchAttempt(schedulerFacade, world, settings, blacklistSettings, ignoredPlayers, attempt + 1));
    }

    private Optional<WildSpawnPair> validatePairAtCenter(SchedulerFacade schedulerFacade,
                                                         Location center,
                                                         WildLocationSettings settings,
                                                         WildBlacklistSettings blacklistSettings,
                                                         Set<UUID> ignoredPlayers) {
        if (!schedulerFacade.isOwnedByCurrentRegion(center, requiredOwnershipRadiusChunks(settings))) {
            return Optional.empty();
        }

        WildSpawnPair rawPair = buildPair(center, settings.spawnDistance());
        Location spawnA = normalizeToGround(rawPair.spawnA());
        Location spawnB = normalizeToGround(rawPair.spawnB());
        if (spawnA == null || spawnB == null) {
            return Optional.empty();
        }

        if (!validator.isSafePair(spawnA, spawnB, settings, blacklistSettings)) {
            return Optional.empty();
        }
        if (validator.isNearOtherPlayers(spawnA, settings.avoidNearbyPlayersRadius(), ignoredPlayers)
                || validator.isNearOtherPlayers(spawnB, settings.avoidNearbyPlayersRadius(), ignoredPlayers)) {
            return Optional.empty();
        }

        faceEachOther(spawnA, spawnB);
        Location normalizedCenter = new Location(
                center.getWorld(),
                (spawnA.getX() + spawnB.getX()) / 2.0D,
                Math.min(spawnA.getY(), spawnB.getY()),
                (spawnA.getZ() + spawnB.getZ()) / 2.0D
        );
        return Optional.of(new WildSpawnPair(normalizedCenter, spawnA, spawnB));
    }

    private Location randomCenter(World world, WildLocationSettings settings) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location origin = world.getSpawnLocation();
        int radius = settings.minRadius() >= settings.maxRadius()
                ? settings.minRadius()
                : random.nextInt(settings.minRadius(), settings.maxRadius() + 1);
        double angle = random.nextDouble(0.0D, Math.PI * 2.0D);

        int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
        int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private WildSpawnPair buildPair(Location center, double distance) {
        double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
        double half = distance / 2.0D;
        double dx = Math.cos(angle) * half;
        double dz = Math.sin(angle) * half;

        Location spawnA = center.clone().add(-dx, 0.0D, -dz);
        Location spawnB = center.clone().add(dx, 0.0D, dz);
        return new WildSpawnPair(center.clone(), spawnA, spawnB);
    }

    private Location normalizeToGround(Location source) {
        if (source == null || source.getWorld() == null) {
            return null;
        }

        World world = source.getWorld();
        int x = source.getBlockX();
        int z = source.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D, source.getYaw(), source.getPitch());
    }

    private void faceEachOther(Location first, Location second) {
        double dx = second.getX() - first.getX();
        double dz = second.getZ() - first.getZ();
        float yawFirst = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawSecond = (float) Math.toDegrees(Math.atan2(dx, -dz));
        first.setYaw(yawFirst);
        second.setYaw(yawSecond);
    }
}
