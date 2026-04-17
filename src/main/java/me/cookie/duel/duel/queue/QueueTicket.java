package me.cookie.duel.duel.queue;

import java.time.Instant;
import java.util.UUID;

public record QueueTicket(UUID playerId, String queueId, Instant joinedAt) {
}
