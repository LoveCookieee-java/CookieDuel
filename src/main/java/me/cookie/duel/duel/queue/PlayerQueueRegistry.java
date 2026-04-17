package me.cookie.duel.duel.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerQueueRegistry {

    private final Map<String, PlayerQueueEntry> entriesById = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerQueueEntry> entriesByOwner = new ConcurrentHashMap<>();

    public synchronized boolean hasId(String id) {
        return entriesById.containsKey(normalize(id));
    }

    public synchronized boolean hasOwner(UUID ownerId) {
        return entriesByOwner.containsKey(ownerId);
    }

    public synchronized Optional<PlayerQueueEntry> byId(String id) {
        return Optional.ofNullable(entriesById.get(normalize(id)));
    }

    public synchronized Optional<PlayerQueueEntry> byOwner(UUID ownerId) {
        return Optional.ofNullable(entriesByOwner.get(ownerId));
    }

    public synchronized boolean add(PlayerQueueEntry entry) {
        String normalizedId = normalize(entry.id());
        if (entriesById.containsKey(normalizedId) || entriesByOwner.containsKey(entry.ownerId())) {
            return false;
        }

        entriesById.put(normalizedId, entry);
        entriesByOwner.put(entry.ownerId(), entry);
        return true;
    }

    public synchronized PlayerQueueEntry removeById(String id) {
        PlayerQueueEntry removed = entriesById.remove(normalize(id));
        if (removed == null) {
            return null;
        }

        removed.deactivate();
        entriesByOwner.remove(removed.ownerId());
        return removed;
    }

    public synchronized PlayerQueueEntry removeByOwner(UUID ownerId) {
        PlayerQueueEntry removed = entriesByOwner.remove(ownerId);
        if (removed == null) {
            return null;
        }

        removed.deactivate();
        entriesById.remove(normalize(removed.id()));
        return removed;
    }

    public synchronized List<PlayerQueueEntry> activeEntries() {
        List<PlayerQueueEntry> entries = new ArrayList<>(entriesById.values());
        entries.sort(Comparator.comparing(PlayerQueueEntry::createdAt).reversed());
        return List.copyOf(entries);
    }

    public synchronized void clear() {
        entriesById.values().forEach(PlayerQueueEntry::deactivate);
        entriesById.clear();
        entriesByOwner.clear();
    }

    private String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
