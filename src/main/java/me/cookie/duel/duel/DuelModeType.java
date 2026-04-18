package me.cookie.duel.duel;

import java.util.Locale;

public enum DuelModeType {
    WILD,
    ARENA;

    public static DuelModeType fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return switch (raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "WILD" -> WILD;
            case "ARENA", "ARENA_INSTANCE" -> ARENA;
            default -> null;
        };
    }

    public String langKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
