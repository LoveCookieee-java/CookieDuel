package me.cookie.duel.config.model;

public record WildLocationSettings(
        String world,
        boolean useRandomSafeLocation,
        double spawnDistance,
        int maxYDifference,
        int minRadius,
        int maxRadius,
        int maxAttempts,
        boolean avoidWater,
        boolean avoidLava,
        boolean avoidDangerousBlocks,
        int avoidNearbyPlayersRadius,
        int localTerrainSampleRadius,
        int maxLocalHeightSpread,
        int maxLocalHeightSpreadDifference,
        int edgeCheckRadius,
        int maxEdgeDrop,
        int nearbyHazardCheckRadius,
        int nearbyObstructionCheckRadius
) {
}
