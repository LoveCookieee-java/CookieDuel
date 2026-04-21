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

    public CreateQueueResult createQueue(UUID ownerId, String ownerName, String ownerMoney, DuelModeType mode) {
        String entryId = ownerName == null ? "" : ownerName.trim();
        if (entryId.isEmpty()) {
            return new CreateQueueResult(CreateQueueStatus.INVALID_ID, null, 0L);
        }
        if (!isModeEnabled(mode)) {
            return new CreateQueueResult(CreateQueueStatus.MODE_DISABLED, null, 0L);
        }
        if (playerQueueRegistry.hasOwner(ownerId)) {
            return new CreateQueueResult(CreateQueueStatus.ALREADY_OWN_QUEUE, null, 0L);
        }
        PlayerQueueEntry existingEntry = playerQueueRegistry.byId(entryId).orElse(null);
        if (existingEntry != null) {
            return new CreateQueueResult(
                    existingEntry.ownerId().equals(ownerId)
                            ? CreateQueueStatus.ALREADY_OWN_QUEUE
                            : CreateQueueStatus.DUPLICATE_ID,
                    null,
                    0L
            );
        }
        if (duelSessionManager.isInSession(ownerId)) {
            return new CreateQueueResult(CreateQueueStatus.IN_SESSION, null, 0L);
        }
        if (mode == DuelModeType.ARENA && resolveArenaTemplateId().isEmpty()) {
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
                entryId,
                ownerId,
                ownerName,
                mode,
                ownerMoney,
                Instant.now()
        );
        if (!playerQueueRegistry.add(entry)) {
            return new CreateQueueResult(
                    playerQueueRegistry.hasOwner(ownerId)
                            ? CreateQueueStatus.ALREADY_OWN_QUEUE
                            : CreateQueueStatus.DUPLICATE_ID,
                    null,
                    0L
            );
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

        String templateId = entry.mode() == DuelModeType.ARENA
                ? resolveArenaTemplateId().orElse(null)
                : null;
        if (entry.mode() == DuelModeType.ARENA && templateId == null) {
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
                templateId
        );
        DuelSessionContext context = duelSessionManager.register(session);
        antiAbuseService.recordQueueJoin(challengerId);
        return new JoinQueueResult(JoinQueueStatus.MATCH_FOUND, removedEntry, session, context, 0L);
    }

    public boolean ownsQueue(UUID playerId) {
        return playerQueueRegistry.hasOwner(playerId);
    }

    public PlayerQueueEntry removeOwnedQueue(UUID playerId) {
        return removeOwnedQueue(playerId, false);
    }

    public PlayerQueueEntry removeOwnedQueue(UUID playerId, boolean applyLeavePenalty) {
        PlayerQueueEntry removed = playerQueueRegistry.removeByOwner(playerId);
        if (removed != null && applyLeavePenalty) {
            antiAbuseService.recordQueueLeave(playerId);
        }
        return removed;
    }

    public boolean leaveQueue(UUID playerId) {
        PlayerQueueEntry removed = removeOwnedQueue(playerId, true);
        if (removed == null) {
            return false;
        }
        return true;
    }

    public List<PlayerQueueEntry> activeEntries() {
        return playerQueueRegistry.activeEntries();
    }

    public List<PlayerQueueEntry> activeEntries(DuelModeType mode) {
        return playerQueueRegistry.activeEntries().stream()
                .filter(entry -> entry.mode() == mode)
                .toList();
    }

    public List<PlayerQueueEntry> randomJoinCandidates(UUID challengerId) {
        return randomJoinCandidates(challengerId, null);
    }

    public List<PlayerQueueEntry> randomJoinCandidates(UUID challengerId, DuelModeType mode) {
        boolean arenaAvailable = resolveArenaTemplateId().isPresent();
        return playerQueueRegistry.activeEntries().stream()
                .filter(PlayerQueueEntry::active)
                .filter(entry -> !entry.ownerId().equals(challengerId))
                .filter(entry -> Bukkit.getPlayer(entry.ownerId()) != null)
                .filter(entry -> mode == null || entry.mode() == mode)
                .filter(entry -> isModeEnabled(entry.mode()))
                .filter(entry -> entry.mode() != DuelModeType.ARENA || arenaAvailable)
                .toList();
    }

    public void removeQueuesNotInMode(DuelModeType mode) {
        playerQueueRegistry.activeEntries().stream()
                .filter(entry -> entry.mode() != mode)
                .map(PlayerQueueEntry::ownerId)
                .toList()
                .forEach(this::removeOwnedQueue);
    }

    public DirectDuelCheckResult checkDirectDuel(UUID requesterId, UUID targetId, DuelModeType mode) {
        if (requesterId.equals(targetId)) {
            return new DirectDuelCheckResult(DirectDuelStatus.SELF_TARGET);
        }
        if (!isModeEnabled(mode)) {
            return new DirectDuelCheckResult(DirectDuelStatus.MODE_DISABLED);
        }
        if (playerQueueRegistry.hasOwner(requesterId)) {
            return new DirectDuelCheckResult(DirectDuelStatus.REQUESTER_HAS_QUEUE);
        }
        if (playerQueueRegistry.hasOwner(targetId)) {
            return new DirectDuelCheckResult(DirectDuelStatus.TARGET_HAS_QUEUE);
        }
        if (duelSessionManager.isInSession(requesterId)) {
            return new DirectDuelCheckResult(DirectDuelStatus.REQUESTER_IN_SESSION);
        }
        if (duelSessionManager.isInSession(targetId)) {
            return new DirectDuelCheckResult(DirectDuelStatus.TARGET_IN_SESSION);
        }
        if (antiAbuseService.isRematchBlocked(requesterId, targetId)) {
            return new DirectDuelCheckResult(DirectDuelStatus.REMATCH_BLOCKED);
        }
        if (mode == DuelModeType.ARENA && resolveArenaTemplateId().isEmpty()) {
            return new DirectDuelCheckResult(DirectDuelStatus.NO_ARENA_TEMPLATE);
        }
        return new DirectDuelCheckResult(DirectDuelStatus.READY);
    }

    public DirectDuelStartResult startDirectDuel(UUID requesterId,
                                                 String requesterName,
                                                 UUID targetId,
                                                 String targetName,
                                                 DuelModeType mode) {
        DirectDuelCheckResult checkResult = checkDirectDuel(requesterId, targetId, mode);
        if (checkResult.status() != DirectDuelStatus.READY) {
            return new DirectDuelStartResult(checkResult.status(), null, null);
        }

        String templateId = mode == DuelModeType.ARENA
                ? resolveArenaTemplateId().orElse(null)
                : null;

        DuelSession session = new DuelSession(
                UUID.randomUUID(),
                requesterId,
                targetId,
                mode,
                requesterName + "-vs-" + targetName,
                templateId
        );
        DuelSessionContext context = duelSessionManager.register(session);
        return new DirectDuelStartResult(DirectDuelStatus.READY, session, context);
    }

    public void clearQueues() {
        playerQueueRegistry.clear();
    }

    private boolean isModeEnabled(DuelModeType mode) {
        return switch (mode) {
            case WILD -> configService.mainConfig().modes().wildEnabled();
            case ARENA -> configService.mainConfig().modes().arena().enabled();
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

    public enum DirectDuelStatus {
        READY,
        SELF_TARGET,
        REQUESTER_HAS_QUEUE,
        TARGET_HAS_QUEUE,
        REQUESTER_IN_SESSION,
        TARGET_IN_SESSION,
        REMATCH_BLOCKED,
        MODE_DISABLED,
        NO_ARENA_TEMPLATE
    }

    public record DirectDuelCheckResult(DirectDuelStatus status) {
    }

    public record DirectDuelStartResult(
            DirectDuelStatus status,
            DuelSession session,
            DuelSessionContext sessionContext
    ) {
    }
}
