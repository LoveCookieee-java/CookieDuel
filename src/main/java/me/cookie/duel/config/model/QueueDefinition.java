package me.cookie.duel.config.model;

import me.cookie.duel.duel.DuelModeType;

public record QueueDefinition(
        String id,
        boolean enabled,
        String displayName,
        DuelModeType mode,
        boolean confirmRequired,
        String templateId
) {
}
