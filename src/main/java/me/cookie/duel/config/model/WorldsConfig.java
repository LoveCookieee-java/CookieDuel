package me.cookie.duel.config.model;

import java.util.Map;

public record WorldsConfig(
        WildLocationSettings wild,
        Map<String, ArenaTemplateDefinition> arenaTemplates
) {
}
