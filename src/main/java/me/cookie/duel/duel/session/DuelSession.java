package me.cookie.duel.duel.session;

import me.cookie.duel.duel.DuelModeType;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class DuelSession {

    private final UUID sessionId;
    private final UUID firstPlayer;
    private final UUID secondPlayer;
    private final DuelModeType mode;
    private final String queueId;
    private final String templateId;
    private final boolean confirmRequired;
    private final AtomicReference<DuelSessionState> state;

    public DuelSession(UUID sessionId,
                       UUID firstPlayer,
                       UUID secondPlayer,
                       DuelModeType mode,
                       String queueId,
                       String templateId,
                       boolean confirmRequired) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.firstPlayer = Objects.requireNonNull(firstPlayer, "firstPlayer");
        this.secondPlayer = Objects.requireNonNull(secondPlayer, "secondPlayer");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.queueId = Objects.requireNonNull(queueId, "queueId");
        this.templateId = templateId;
        this.confirmRequired = confirmRequired;
        this.state = new AtomicReference<>(DuelSessionState.MATCH_FOUND);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID firstPlayer() {
        return firstPlayer;
    }

    public UUID secondPlayer() {
        return secondPlayer;
    }

    public DuelModeType mode() {
        return mode;
    }

    public String queueId() {
        return queueId;
    }

    public String templateId() {
        return templateId;
    }

    public boolean confirmRequired() {
        return confirmRequired;
    }

    public DuelSessionState state() {
        return state.get();
    }

    public boolean containsPlayer(UUID playerId) {
        return firstPlayer.equals(playerId) || secondPlayer.equals(playerId);
    }

    public UUID opponentOf(UUID playerId) {
        if (firstPlayer.equals(playerId)) {
            return secondPlayer;
        }
        if (secondPlayer.equals(playerId)) {
            return firstPlayer;
        }
        throw new IllegalArgumentException("Player " + playerId + " is not part of session " + sessionId);
    }

    public boolean transition(DuelSessionState expected, DuelSessionState next) {
        return state.compareAndSet(expected, next);
    }

    public boolean transitionAny(Set<DuelSessionState> expectedStates, DuelSessionState next) {
        DuelSessionState current = state.get();
        if (!expectedStates.contains(current)) {
            return false;
        }
        return state.compareAndSet(current, next);
    }
}
