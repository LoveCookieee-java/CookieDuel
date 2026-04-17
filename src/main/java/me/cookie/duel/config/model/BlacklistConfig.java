package me.cookie.duel.config.model;

public record BlacklistConfig(
        WildBlacklistSettings wild,
        int invalidEntryCount
) {
}
