package me.cookie.duel.config.model;

public record AntiAbuseSettings(
        int queueJoinCooldownSeconds,
        int queueLeavePenaltySeconds,
        int rematchBlockSeconds
) {
}
