package me.cookie.duel.duel.teleport.wild;

import me.cookie.duel.config.model.WildBlacklistSettings;
import me.cookie.duel.config.model.WildLocationSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class WildLocationValidator {

    public boolean isSafePair(Location first,
                              Location second,
                              WildLocationSettings settings,
                              WildBlacklistSettings blacklistSettings) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        if (Math.abs(first.getBlockY() - second.getBlockY()) > settings.maxYDifference()) {
            return false;
        }

        SpawnAssessment firstAssessment = assessSpawn(first, settings, blacklistSettings);
        if (!firstAssessment.safe()) {
            return false;
        }

        SpawnAssessment secondAssessment = assessSpawn(second, settings, blacklistSettings);
        if (!secondAssessment.safe()) {
            return false;
        }

        return Math.abs(firstAssessment.localHeightSpread() - secondAssessment.localHeightSpread())
                <= settings.maxLocalHeightSpreadDifference();
    }

    public boolean isNearOtherPlayers(Location location, int radius, Set<UUID> ignoredPlayers) {
        if (location == null || location.getWorld() == null || radius <= 0) {
            return false;
        }

        return !location.getWorld().getNearbyPlayers(location, radius, player -> !ignoredPlayers.contains(player.getUniqueId())).isEmpty();
    }

    private SpawnAssessment assessSpawn(Location location,
                                        WildLocationSettings settings,
                                        WildBlacklistSettings blacklistSettings) {
        World world = location.getWorld();
        if (world == null) {
            return SpawnAssessment.invalid();
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 3) {
            return SpawnAssessment.invalid();
        }

        Block floor = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block aboveHead = world.getBlockAt(x, y + 2, z);

        Material floorType = floor.getType();
        Material feetType = feet.getType();
        Material headType = head.getType();
        Material aboveHeadType = aboveHead.getType();

        if (!isSafeFloor(floorType, settings, blacklistSettings)) {
            return SpawnAssessment.invalid();
        }

        if (!feetType.isAir() || !headType.isAir() || !aboveHeadType.isAir()) {
            return SpawnAssessment.invalid();
        }

        int localHeightSpread = calculateLocalHeightSpread(location, settings);
        if (localHeightSpread > settings.maxLocalHeightSpread()) {
            return SpawnAssessment.invalid();
        }

        if (hasUnsafeEdge(location, settings)) {
            return SpawnAssessment.invalid();
        }
        if (hasNearbyHazards(location, settings, blacklistSettings)) {
            return SpawnAssessment.invalid();
        }
        if (hasNearbyObstructions(location, settings, blacklistSettings)) {
            return SpawnAssessment.invalid();
        }

        return new SpawnAssessment(true, localHeightSpread);
    }

    private boolean isSafeFloor(Material floorType,
                                WildLocationSettings settings,
                                WildBlacklistSettings blacklistSettings) {
        if (!floorType.isSolid()) {
            return false;
        }
        if (blacklistSettings.floorBlacklist().contains(floorType)) {
            return false;
        }
        if (settings.avoidLava() && floorType == Material.LAVA) {
            return false;
        }
        if (settings.avoidWater() && floorType == Material.WATER) {
            return false;
        }
        return !settings.avoidDangerousBlocks() || !HazardBlockPolicy.isNativeHazard(floorType);
    }

    private int calculateLocalHeightSpread(Location location, WildLocationSettings settings) {
        World world = location.getWorld();
        if (world == null) {
            return Integer.MAX_VALUE;
        }

        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        int minFloorY = location.getBlockY() - 1;
        int maxFloorY = location.getBlockY() - 1;
        int radius = Math.max(0, settings.localTerrainSampleRadius());

        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                int floorY = world.getHighestBlockYAt(centerX + offsetX, centerZ + offsetZ);
                minFloorY = Math.min(minFloorY, floorY);
                maxFloorY = Math.max(maxFloorY, floorY);
            }
        }

        return maxFloorY - minFloorY;
    }

    private boolean hasUnsafeEdge(Location location, WildLocationSettings settings) {
        World world = location.getWorld();
        if (world == null) {
            return true;
        }

        int centerFloorY = location.getBlockY() - 1;
        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        int radius = Math.max(0, settings.edgeCheckRadius());

        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                int floorY = world.getHighestBlockYAt(centerX + offsetX, centerZ + offsetZ);
                if (centerFloorY - floorY > settings.maxEdgeDrop()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasNearbyHazards(Location location,
                                     WildLocationSettings settings,
                                     WildBlacklistSettings blacklistSettings) {
        World world = location.getWorld();
        if (world == null) {
            return true;
        }

        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        int radius = Math.max(0, settings.nearbyHazardCheckRadius());

        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }

                int sampleX = centerX + offsetX;
                int sampleZ = centerZ + offsetZ;
                int sampleFloorY = world.getHighestBlockYAt(sampleX, sampleZ);

                Material floorType = world.getBlockAt(sampleX, sampleFloorY, sampleZ).getType();
                Material feetType = world.getBlockAt(sampleX, sampleFloorY + 1, sampleZ).getType();
                Material headType = world.getBlockAt(sampleX, sampleFloorY + 2, sampleZ).getType();

                if (isNearbyHazardMaterial(floorType, settings, blacklistSettings)
                        || isNearbyHazardMaterial(feetType, settings, blacklistSettings)
                        || isNearbyHazardMaterial(headType, settings, blacklistSettings)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasNearbyObstructions(Location location,
                                          WildLocationSettings settings,
                                          WildBlacklistSettings blacklistSettings) {
        World world = location.getWorld();
        if (world == null) {
            return true;
        }

        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        int radius = Math.max(0, settings.nearbyObstructionCheckRadius());

        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }

                Material feetType = world.getBlockAt(centerX + offsetX, centerY, centerZ + offsetZ).getType();
                Material headType = world.getBlockAt(centerX + offsetX, centerY + 1, centerZ + offsetZ).getType();

                if (isNearbyObstructionMaterial(feetType, blacklistSettings)
                        || isNearbyObstructionMaterial(headType, blacklistSettings)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isNearbyHazardMaterial(Material material,
                                           WildLocationSettings settings,
                                           WildBlacklistSettings blacklistSettings) {
        if (blacklistSettings.nearbyBlacklist().contains(material)) {
            return true;
        }
        if (settings.avoidLava() && material == Material.LAVA) {
            return true;
        }
        if (settings.avoidWater() && material == Material.WATER) {
            return true;
        }
        return settings.avoidDangerousBlocks() && HazardBlockPolicy.isNativeHazard(material);
    }

    private boolean isNearbyObstructionMaterial(Material material, WildBlacklistSettings blacklistSettings) {
        if (material.isAir()) {
            return false;
        }
        if (blacklistSettings.bodyBlacklist().contains(material)) {
            return true;
        }
        return HazardBlockPolicy.isNativeBodyObstruction(material);
    }

    private record SpawnAssessment(boolean safe, int localHeightSpread) {

        private static SpawnAssessment invalid() {
            return new SpawnAssessment(false, Integer.MAX_VALUE);
        }
    }
}
