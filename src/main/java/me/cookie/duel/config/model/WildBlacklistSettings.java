package me.cookie.duel.config.model;

import org.bukkit.Material;

import java.util.Set;

public record WildBlacklistSettings(
        Set<Material> floorBlacklist,
        Set<Material> bodyBlacklist,
        Set<Material> nearbyBlacklist
) {

    public WildBlacklistSettings {
        floorBlacklist = Set.copyOf(floorBlacklist);
        bodyBlacklist = Set.copyOf(bodyBlacklist);
        nearbyBlacklist = Set.copyOf(nearbyBlacklist);
    }
}
