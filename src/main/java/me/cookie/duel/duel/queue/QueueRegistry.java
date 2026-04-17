package me.cookie.duel.duel.queue;

import me.cookie.duel.config.model.QueueDefinition;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class QueueRegistry {

    private final Map<String, QueueDefinition> queueDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Deque<QueueTicket>> waitingByQueue = new ConcurrentHashMap<>();
    private final Map<UUID, QueueTicket> ticketsByPlayer = new ConcurrentHashMap<>();

    public QueueRegistry(Map<String, QueueDefinition> initialDefinitions) {
        replaceDefinitions(initialDefinitions);
    }

    public synchronized void replaceDefinitions(Map<String, QueueDefinition> newDefinitions) {
        queueDefinitions.clear();
        queueDefinitions.putAll(newDefinitions);
        waitingByQueue.clear();
        ticketsByPlayer.clear();
    }

    public Optional<QueueDefinition> definition(String queueId) {
        return Optional.ofNullable(queueDefinitions.get(queueId));
    }

    public Collection<QueueDefinition> definitions() {
        return queueDefinitions.values();
    }

    public boolean isQueued(UUID playerId) {
        return ticketsByPlayer.containsKey(playerId);
    }

    public synchronized void add(QueueTicket ticket) {
        if (ticketsByPlayer.containsKey(ticket.playerId())) {
            return;
        }
        waitingByQueue.computeIfAbsent(ticket.queueId(), ignored -> new ArrayDeque<>()).addLast(ticket);
        ticketsByPlayer.put(ticket.playerId(), ticket);
    }

    public synchronized QueueTicket remove(UUID playerId) {
        QueueTicket ticket = ticketsByPlayer.remove(playerId);
        if (ticket == null) {
            return null;
        }
        Deque<QueueTicket> queue = waitingByQueue.get(ticket.queueId());
        if (queue != null) {
            queue.removeIf(entry -> entry.playerId().equals(playerId));
        }
        return ticket;
    }

    public synchronized QueueTicket pollMatchingTicket(String queueId, Predicate<QueueTicket> predicate) {
        Deque<QueueTicket> queue = waitingByQueue.get(queueId);
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            QueueTicket candidate = iterator.next();
            if (!predicate.test(candidate)) {
                continue;
            }
            iterator.remove();
            ticketsByPlayer.remove(candidate.playerId());
            return candidate;
        }
        return null;
    }
}
