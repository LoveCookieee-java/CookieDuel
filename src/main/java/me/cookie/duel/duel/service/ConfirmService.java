package me.cookie.duel.duel.service;

import me.cookie.duel.duel.session.DuelSession;
import me.cookie.duel.scheduler.SchedulerFacade;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmService {

    private final SchedulerFacade schedulerFacade;
    private final Map<UUID, ConfirmationTracker> trackers = new ConcurrentHashMap<>();

    public ConfirmService(SchedulerFacade schedulerFacade) {
        this.schedulerFacade = schedulerFacade;
    }

    public void start(DuelSession session, long timeoutTicks, Runnable onTimeout) {
        SchedulerFacade.TaskHandle timeoutTask = schedulerFacade.runLater(() -> {
            clear(session.sessionId());
            onTimeout.run();
        }, timeoutTicks);
        trackers.put(session.sessionId(), new ConfirmationTracker(ConcurrentHashMap.newKeySet(), timeoutTask));
    }

    public ConfirmationResult accept(DuelSession session, UUID playerId) {
        ConfirmationTracker tracker = trackers.get(session.sessionId());
        if (tracker == null) {
            return ConfirmationResult.NOT_TRACKING;
        }
        if (!session.containsPlayer(playerId)) {
            return ConfirmationResult.NOT_TRACKING;
        }
        if (!tracker.acceptedPlayers().add(playerId)) {
            return ConfirmationResult.ALREADY_ACCEPTED;
        }
        if (tracker.acceptedPlayers().size() == 2) {
            tracker.timeoutTask().cancel();
            trackers.remove(session.sessionId());
            return ConfirmationResult.BOTH_ACCEPTED;
        }
        return ConfirmationResult.ACCEPTED;
    }

    public void clear(UUID sessionId) {
        ConfirmationTracker tracker = trackers.remove(sessionId);
        if (tracker != null) {
            tracker.timeoutTask().cancel();
        }
    }

    private record ConfirmationTracker(Set<UUID> acceptedPlayers, SchedulerFacade.TaskHandle timeoutTask) {
    }

    public enum ConfirmationResult {
        ACCEPTED,
        ALREADY_ACCEPTED,
        BOTH_ACCEPTED,
        NOT_TRACKING
    }
}
