package me.cookie.duel.duel.service;

import me.cookie.duel.config.model.QueueDefinition;
import me.cookie.duel.duel.queue.QueueRegistry;
import me.cookie.duel.duel.queue.QueueTicket;
import me.cookie.duel.duel.session.DuelSession;
import me.cookie.duel.duel.session.DuelSessionContext;
import me.cookie.duel.duel.session.DuelSessionManager;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.UUID;

public final class MatchmakingService {

    private final QueueRegistry queueRegistry;
    private final DuelSessionManager duelSessionManager;
    private final AntiAbuseService antiAbuseService;

    public MatchmakingService(QueueRegistry queueRegistry,
                              DuelSessionManager duelSessionManager,
                              AntiAbuseService antiAbuseService) {
        this.queueRegistry = queueRegistry;
        this.duelSessionManager = duelSessionManager;
        this.antiAbuseService = antiAbuseService;
    }

    public JoinResult joinQueue(UUID playerId, String queueId) {
        QueueDefinition definition = queueRegistry.definition(queueId).orElse(null);
        if (definition == null) {
            return new JoinResult(JoinStatus.UNKNOWN_QUEUE, null, null, null, 0L);
        }
        if (!definition.enabled()) {
            return new JoinResult(JoinStatus.QUEUE_DISABLED, definition, null, null, 0L);
        }
        if (queueRegistry.isQueued(playerId)) {
            return new JoinResult(JoinStatus.ALREADY_QUEUED, definition, null, null, 0L);
        }
        if (duelSessionManager.isInSession(playerId)) {
            return new JoinResult(JoinStatus.IN_SESSION, definition, null, null, 0L);
        }

        AntiAbuseService.JoinCheckResult joinCheck = antiAbuseService.checkCanJoin(playerId);
        if (joinCheck.reason() == AntiAbuseService.JoinBlockReason.JOIN_COOLDOWN) {
            return new JoinResult(JoinStatus.JOIN_COOLDOWN, definition, null, null, joinCheck.secondsRemaining());
        }
        if (joinCheck.reason() == AntiAbuseService.JoinBlockReason.LEAVE_PENALTY) {
            return new JoinResult(JoinStatus.LEAVE_PENALTY, definition, null, null, joinCheck.secondsRemaining());
        }

        QueueTicket matchedTicket = queueRegistry.pollMatchingTicket(queueId, ticket -> {
            if (ticket.playerId().equals(playerId)) {
                return false;
            }
            if (Bukkit.getPlayer(ticket.playerId()) == null) {
                return false;
            }
            return !antiAbuseService.isRematchBlocked(playerId, ticket.playerId());
        });

        antiAbuseService.recordQueueJoin(playerId);

        if (matchedTicket == null) {
            queueRegistry.add(new QueueTicket(playerId, queueId, Instant.now()));
            return new JoinResult(JoinStatus.QUEUED, definition, null, null, 0L);
        }

        DuelSession session = new DuelSession(
                UUID.randomUUID(),
                matchedTicket.playerId(),
                playerId,
                definition.mode(),
                definition.id(),
                definition.templateId(),
                definition.confirmRequired()
        );
        DuelSessionContext context = duelSessionManager.register(session, definition);
        return new JoinResult(JoinStatus.MATCH_FOUND, definition, session, context, antiAbuseService.rematchSecondsRemaining(playerId, matchedTicket.playerId()));
    }

    public boolean leaveQueue(UUID playerId) {
        QueueTicket removed = queueRegistry.remove(playerId);
        if (removed == null) {
            return false;
        }
        antiAbuseService.recordQueueLeave(playerId);
        return true;
    }

    public record JoinResult(
            JoinStatus status,
            QueueDefinition queueDefinition,
            DuelSession session,
            DuelSessionContext sessionContext,
            long secondsRemaining
    ) {
    }

    public enum JoinStatus {
        QUEUED,
        MATCH_FOUND,
        ALREADY_QUEUED,
        IN_SESSION,
        UNKNOWN_QUEUE,
        QUEUE_DISABLED,
        JOIN_COOLDOWN,
        LEAVE_PENALTY
    }
}
