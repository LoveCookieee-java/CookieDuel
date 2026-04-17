package me.cookie.duel.config.model;

public record MainConfig(
        boolean debug,
        LobbySettings lobby,
        ModeSettings modes,
        DuelSettings duel,
        AntiAbuseSettings antiAbuse
) {
}
