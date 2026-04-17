package me.cookie.duel.duel.service;

import me.cookie.duel.config.ConfigService;
import me.cookie.duel.config.model.ArenaTemplateDefinition;
import me.cookie.duel.duel.DuelModeType;
import me.cookie.duel.duel.queue.PlayerQueueEntry;
import me.cookie.duel.duel.queue.PlayerQueueRegistry;
import me.cookie.duel.duel.session.DuelSession;
import me.cookie.duel.duel.session.DuelSessionContext;
import me.cookie.duel.duel.session.DuelSessionManager;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MatchmakingService {

    private final ConfigService configService;
    private final PlayerQueueRegistry playerQueueRegistry;
    private final DuelSessionManager duelSessionManager;
    private final AntiAbuseService antiAbuseService;

    public MatchmakingService(ConfigService configService,
                              PlayerQueueRegistry playerQueueRegistry,
                              DuelSessionManager duelSessionManager,
                              AntiAbuseService antiAbuseService) {
        this.configService = configService;
        this.playerQueueRegistry = playerQueueRegistry;
        this.duelSessionManager = duelSessionManager;
        this.antiAbuseService = antiAbuseService;
    }

    public CreateQueueResult createQueue(UUID ownerId, String ownerName, String queueId, DuelModeType mode) {
        String trimmedId = queueId == null ? "" : queueId.trim();
        if (trimmedId.isEmpty()) {
            return new CreateQueueResult(CreateQueueStatus.INVALID_ID, null, 0L);
        }
        if (!isModeEnabled(mode)) {
            return new CreateQueueResult(CreateQueueStatus.MODE_DISABLED, null, 0L);
        }
        if (playerQueueRegistry.hasId(trimmedId)) {
            return new CreateQueueResult(CreateQueueStatus.DUPLICATE_ID, null, 0L);
        }
        if (playerQueueRegistry.hasOwner(ownerId)) {
            return new CreateQueueResult(CreateQueueStatus.ALREADY_OWN_QUEUE, null, 0L);
        }
        if (duelSessionManager.isInSession(ownerId)) {
            return new CreateQueueResult(CreateQueueStatus.IN_SESSION, null, 0L);
        }
        if (mode == DuelModeType.ARENA_INSTANCE && resolveArenaTemplateId().isEmpty()) {
            return new CreateQueueResult(CreateQueueStatus.NO_ARENA_TEMPLATE, null, 0L);
        }

        AntiAbuseService.JoinCheckResult joinCheck = antiAbuseService.checkCanJoin(ownerId);
        if (joinCheck.reason() == AntiAbuseService.JoinBlockReason.JOIN_COOLDOWN) {
            return new CreateQueueResult(CreateQueueStatus.JOIN_COOLDOWN, null, joinCheck.secondsRemaining());
        }
        if (joinCheck.reason() == AntiAbuseService.JoinBlockReason.LEAVE_PENALTY) {
            return new CreateQueueResult(CreateQueueStatus.LEAVE_PENALTY, null, joinCheck.secondsRemaining());
        }

        PlayerQueueEntry entry = new PlayerQueueEntry(
                trimmedId,
                ownerId,
                ownerName,
                mode,
                0L,
                Instant.now()
        );
        if (!playerQueueRegistry.add(entry)) {
            return new CreateQueueResult(CreateQueueStatus.DUPLICATE_ID, null, 0L);
        }

        antiAbuseService.recordQueueJoin(ownerId);
        return new CreateQueueResult(CreateQueueStatus.CREATED, entry, 0L);
    }

    public JoinQueueResult joinQueue(UUID challengerId, String entryId) {
        PlayerQueueEntry entry = playerQueueRegistry.byId(entryId).orElse(null);
        if (entry == null || !entry.active()) {
            return new JoinQueueResult(JoinQueueStatus.NOT_FOUND, null, null, null, 0L);
        }
        if (entry.ownerId().equals(challengerId)) {
            return new JoinQueueResult(JoinQueueStatus.OWN_QUEUE, entry, null, null, 0L);
        }
        if (!isModeEnabled(entry.mode())) {
            return new JoinQueueResult(JoinQueueStatus.MODE_DISABLED, entry, null, null, 0L);
        }
        if (playerQueueRegistry.hasOwner(challengerId)) {
            return new JoinQueueResult(JoinQueueStatus.ALREADY_OWN_QUEUE, entry, null, null, 0L);
        }
        if (duelSessionManager.isInSession(challengerId)) {
            return new JoinQueueResult(JoinQueueStatus.IN_SESSION, entry, null, null, 0L);
        }
        if (Bukkit.getPlayer(entry.ownerId()) == null) {
            playerQueueRegistry.removeByOwner(entry.ownerId());
            return new JoinQueueResult(JoinQueueStatus.OWNER_UNAVAILABLE, entry, null, null, 0L);
        }

        AntiAbuseService.JoinCheckResult joinCheck = antiAbuseService.checkCanJoin(challengerId);
        if (joinCheck.reason() == AntiAbuseService.JoinBlockReason.JOIN_COOLDOWN) {
            return new JoinQueueResult(JoinQueueStatus.JOIN_COOLDOWN, entry, null, null, joinCheck.secondsRemaining());
        }
        if (joinCheck.reason() == AntiAbuseService.JoinBlockReason.LEAVE_PENALTY) {
            return new JoinQueueResult(JoinQueueStatus.LEAVE_PENALTY, entry, null, null, joinCheck.secondsRemaining());
        }
        if (antiAbuseService.isRematchBlocked(challengerId, entry.ownerId())) {
            return new JoinQueueResult(
                    JoinQueueStatus.REMATCH_BLOCKED,
                    entry,
                    null,
                    null,
                    antiAbuseService.rematchSecondsRemaining(challengerId, entry.ownerId())
            );
        }

        String templateId = entry.mode() == DuelModeType.ARENA_INSTANCE
                ? resolveArenaTemplateId().orElse(null)
                : null;
        if (entry.mode() == DuelModeType.ARENA_INSTANCE && templateId == null) {
            return new JoinQueueResult(JoinQueueStatus.NO_ARENA_TEMPLATE, entry, null, null, 0L);
        }

        PlayerQueueEntry removedEntry = playerQueueRegistry.removeById(entry.id());
        if (removedEntry == null) {
            return new JoinQueueResult(JoinQueueStatus.NOT_FOUND, entry, null, null, 0L);
        }

        DuelSession session = new DuelSession(
                UUID.randomUUID(),
                removedEntry.ownerId(),
                challengerId,
                removedEntry.mode(),
                removedEntry.id(),
                templateId,
                true
        );
        DuelSessionContext context = duelSessionManager.register(session);
        antiAbuseService.recordQueueJoin(challengerId);
        return new JoinQueueResult(JoinQueueStatus.MATCH_FOUND, removedEntry, session, context, 0L);
    }

    public PlayerQueueEntry removeOwnedQueue(UUID playerId) {
        return playerQueueRegistry.removeByOwner(playerId);
    }

    public boolean leaveQueue(UUID playerId) {
        PlayerQueueEntry removed = playerQueueRegistry.removeByOwner(playerId);
        if (removed == null) {
            return false;
        }
        antiAbuseService.recordQueueLeave(playerId);
        return true;
    }

    public Optional<PlayerQueueEntry> queueEntry(String entryId) {
        return playerQueueRegistry.byId(entryId);
    }

    public List<PlayerQueueEntry> activeEntries() {
        return playerQueueRegistry.activeEntries();
    }

    public void clearQueues() {
        playerQueueRegistry.clear();
    }

    private boolean isModeEnabled(DuelModeType mode) {
        return switch (mode) {
            case WILD -> configService.mainConfig().modes().wildEnabled();
            case ARENA_INSTANCE -> configService.mainConfig().modes().arenaInstance().enabled();
        };
    }

    private Optional<String> resolveArenaTemplateId() {
        String configuredDefault = configService.worldsConfig().defaultArenaTemplateId();
        if (configuredDefault != null && !configuredDefault.isBlank()) {
            ArenaTemplateDefinition template = configService.worldsConfig().arenaTemplates().get(configuredDefault);
            if (template != null && template.enabled()) {
                return Optional.of(template.id());
            }
        }

        return configService.worldsConfig().arenaTemplates().values().stream()
                .filter(ArenaTemplateDefinition::enabled)
                .map(ArenaTemplateDefinition::id)
                .findFirst();
    }

    public record CreateQueueResult(CreateQueueStatus status, PlayerQueueEntry entry, long secondsRemaining) {
    }

    public record JoinQueueResult(
            JoinQueueStatus status,
            PlayerQueueEntry entry,
            DuelSession session,
            DuelSessionContext sessionContext,
            long secondsRemaining
    ) {
    }

    public enum CreateQueueStatus {
        CREATED,
        INVALID_ID,
        DUPLICATE_ID,
        ALREADY_OWN_QUEUE,
        IN_SESSION,
        JOIN_COOLDOWN,
        LEAVE_PENALTY,
        MODE_DISABLED,
        NO_ARENA_TEMPLATE
    }

    public enum JoinQueueStatus {
        MATCH_FOUND,
        NOT_FOUND,
        OWN_QUEUE,
        OWNER_UNAVAILABLE,
        ALREADY_OWN_QUEUE,
        IN_SESSION,
        JOIN_COOLDOWN,
        LEAVE_PENALTY,
        REMATCH_BLOCKED,
        MODE_DISABLED,
        NO_ARENA_TEMPLATE
    }
}
