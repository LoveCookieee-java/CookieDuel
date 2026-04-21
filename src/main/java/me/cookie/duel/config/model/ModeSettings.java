package me.cookie.duel.config.model;

import me.cookie.duel.duel.DuelModeType;

import java.util.Optional;

public record ModeSettings(
        boolean wildEnabled,
        ArenaModeSettings arena
) {

    public int enabledModeCount() {
        int enabledCount = 0;
        if (wildEnabled) {
            enabledCount++;
        }
        if (arena.enabled()) {
            enabledCount++;
        }
        return enabledCount;
    }

    public Optional<DuelModeType> activeMode() {
        if (wildEnabled == arena.enabled()) {
            return Optional.empty();
        }
        return Optional.of(wildEnabled ? DuelModeType.WILD : DuelModeType.ARENA);
    }
}
