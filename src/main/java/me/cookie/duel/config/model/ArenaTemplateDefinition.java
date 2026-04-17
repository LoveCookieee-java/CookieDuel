package me.cookie.duel.config.model;

public record ArenaTemplateDefinition(
        String id,
        boolean enabled,
        String templateWorldName,
        String instanceWorldPrefix,
        SerializableLocation spawnA,
        SerializableLocation spawnB,
        InstanceWorldSettings settings
) {
}
