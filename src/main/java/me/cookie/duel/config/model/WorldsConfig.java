package me.cookie.duel.config.model;

import java.util.Map;

public record WorldsConfig(
        WildLocationSettings wild,
        String defaultArenaTemplateId,
        Map<String, ArenaTemplateDefinition> arenaTemplates
) {
}
