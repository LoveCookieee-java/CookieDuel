package me.cookie.duel.config.model;

public record ArenaModeSettings(
        boolean enabled,
        int maxActiveInstances,
        boolean cleanupLeftoversOnStartup,
        int instanceCreateTimeoutSeconds,
        int instanceDeleteTimeoutSeconds
) {
}
