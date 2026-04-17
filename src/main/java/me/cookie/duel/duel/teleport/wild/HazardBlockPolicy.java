package me.cookie.duel.duel.teleport.wild;

import org.bukkit.Material;

public final class HazardBlockPolicy {

    private HazardBlockPolicy() {
    }

    public static boolean isNativeHazard(Material material) {
        return switch (material) {
            case LAVA, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, FIRE, SOUL_FIRE,
                    POWDER_SNOW, COBWEB, SWEET_BERRY_BUSH, POINTED_DRIPSTONE -> true;
            default -> false;
        };
    }

    public static boolean isNativeBodyObstruction(Material material) {
        return material.isSolid()
                || material == Material.LAVA
                || material == Material.WATER
                || material == Material.COBWEB
                || material == Material.POWDER_SNOW
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.POINTED_DRIPSTONE;
    }
}
