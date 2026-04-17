package me.cookie.duel.config.model;

public record InstanceWorldSettings(
        boolean autoSetGamerules,
        boolean allowBlockBreak,
        boolean allowBlockPlace,
        double borderSize
) {
}
