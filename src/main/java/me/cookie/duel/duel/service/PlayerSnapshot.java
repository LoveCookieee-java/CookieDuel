package me.cookie.duel.duel.service;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public record PlayerSnapshot(
        ItemStack[] inventoryContents,
        ItemStack[] armorContents,
        ItemStack offHand,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        float experience,
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        Location returnLocation
) {
}
