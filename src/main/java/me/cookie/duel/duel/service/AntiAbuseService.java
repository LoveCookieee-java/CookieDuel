package me.cookie.duel.duel.service;

import me.cookie.duel.config.ConfigService;
import me.cookie.duel.config.model.AntiAbuseSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiAbuseService {

    private final ConfigService configService;
    private final Map<UUID, Instant> joinCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> leavePenaltyUntil = new ConcurrentHashMap<>();
    private final Map<String, Instant> rematchBlockedUntil = new ConcurrentHashMap<>();

    public AntiAbuseService(ConfigService configService) {
        this.configService = configService;
    }

    public JoinCheckResult checkCanJoin(UUID playerId) {
        Instant now = Instant.now();
        Instant joinCooldown = joinCooldownUntil.get(playerId);
        if (joinCooldown != null && now.isBefore(joinCooldown)) {
            return new JoinCheckResult(JoinBlockReason.JOIN_COOLDOWN, secondsRemaining(now, joinCooldown));
        }

        Instant leavePenalty = leavePenaltyUntil.get(playerId);
        if (leavePenalty != null && now.isBefore(leavePenalty)) {
            return new JoinCheckResult(JoinBlockReason.LEAVE_PENALTY, secondsRemaining(now, leavePenalty));
        }

        return new JoinCheckResult(JoinBlockReason.NONE, 0L);
    }

    public boolean isRematchBlocked(UUID first, UUID second) {
        Instant blockedUntil = rematchBlockedUntil.get(pairKey(first, second));
        return blockedUntil != null && Instant.now().isBefore(blockedUntil);
    }

    public long rematchSecondsRemaining(UUID first, UUID second) {
        Instant blockedUntil = rematchBlockedUntil.get(pairKey(first, second));
        if (blockedUntil == null || !Instant.now().isBefore(blockedUntil)) {
            return 0L;
        }
        return secondsRemaining(Instant.now(), blockedUntil);
    }

    public void recordQueueJoin(UUID playerId) {
        AntiAbuseSettings settings = configService.mainConfig().antiAbuse();
        joinCooldownUntil.put(playerId, Instant.now().plusSeconds(settings.queueJoinCooldownSeconds()));
    }

    public void recordQueueLeave(UUID playerId) {
        AntiAbuseSettings settings = configService.mainConfig().antiAbuse();
        leavePenaltyUntil.put(playerId, Instant.now().plusSeconds(settings.queueLeavePenaltySeconds()));
    }

    public void recordSessionEnd(UUID first, UUID second) {
        AntiAbuseSettings settings = configService.mainConfig().antiAbuse();
        rematchBlockedUntil.put(pairKey(first, second), Instant.now().plusSeconds(settings.rematchBlockSeconds()));
    }

    private String pairKey(UUID first, UUID second) {
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }

    private long secondsRemaining(Instant from, Instant to) {
        return Math.max(1L, Duration.between(from, to).toSeconds());
    }

    public enum JoinBlockReason {
        NONE,
        JOIN_COOLDOWN,
        LEAVE_PENALTY
    }

    public record JoinCheckResult(JoinBlockReason reason, long secondsRemaining) {
    }
}
