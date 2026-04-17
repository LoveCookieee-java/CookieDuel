package me.cookie.duel.duel.session;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DuelSessionManager {

    private final Map<UUID, DuelSessionContext> sessionsById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToSessionId = new ConcurrentHashMap<>();

    public DuelSessionContext register(DuelSession session) {
        DuelSessionContext context = new DuelSessionContext(session);
        sessionsById.put(session.sessionId(), context);
        playerToSessionId.put(session.firstPlayer(), session.sessionId());
        playerToSessionId.put(session.secondPlayer(), session.sessionId());
        return context;
    }

    public Optional<DuelSessionContext> bySessionId(UUID sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public Optional<DuelSessionContext> byPlayer(UUID playerId) {
        UUID sessionId = playerToSessionId.get(playerId);
        return sessionId == null ? Optional.empty() : bySessionId(sessionId);
    }

    public boolean isInSession(UUID playerId) {
        return playerToSessionId.containsKey(playerId);
    }

    public void remove(DuelSession session) {
        sessionsById.remove(session.sessionId());
        playerToSessionId.remove(session.firstPlayer());
        playerToSessionId.remove(session.secondPlayer());
    }

    public Collection<DuelSessionContext> activeContexts() {
        return sessionsById.values();
    }
}
