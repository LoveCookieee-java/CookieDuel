package me.cookie.duel.config.model;

public record MainConfig(
        boolean debug,
        ModeSettings modes,
        DuelSettings duel,
        AntiAbuseSettings antiAbuse
) {
}
