package me.cookie.duel.config.model;

import org.bukkit.Location;
import org.bukkit.World;

public record SerializableLocation(double x, double y, double z, float yaw, float pitch) {

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
