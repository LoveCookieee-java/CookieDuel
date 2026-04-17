package me.cookie.duel.config.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record LobbySettings(
        boolean enabled,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
