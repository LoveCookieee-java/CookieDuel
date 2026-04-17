package me.cookie.duel.config.model;

public record DuelSettings(
        int confirmTimeoutSeconds,
        int prestartCountdownSeconds,
        int maxFightSeconds,
        boolean restoreInventoryAfterDuel,
        boolean teleportBackToLobbyAfterDuel
) {
}
