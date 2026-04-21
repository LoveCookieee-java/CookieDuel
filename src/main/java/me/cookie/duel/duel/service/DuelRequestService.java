package me.cookie.duel.duel.service;

import me.cookie.duel.duel.DuelModeType;
import me.cookie.duel.scheduler.SchedulerFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DuelRequestService {

    private static final long REQUEST_TIMEOUT_TICKS = 20L * 30L;

    private final SchedulerFacade schedulerFacade;
    private final Map<UUID, PendingDuelRequest> incomingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDuelRequest> outgoingByRequester = new ConcurrentHashMap<>();

    public DuelRequestService(SchedulerFacade schedulerFacade) {
        this.schedulerFacade = schedulerFacade;
    }

    public CreateRequestResult create(UUID requesterId,
                                      String requesterName,
                                      UUID targetId,
                                      String targetName,
                                      DuelModeType mode,
                                      Consumer<PendingDuelRequest> onExpire) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(requesterName, "requesterName");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(onExpire, "onExpire");

        if (requesterId.equals(targetId)) {
            return new CreateRequestResult(CreateRequestStatus.SELF_TARGET, null);
        }

        PendingDuelRequest requesterOutgoing = outgoingByRequester.get(requesterId);
        if (requesterOutgoing != null) {
            return new CreateRequestResult(
                    requesterOutgoing.targetId().equals(targetId)
                            ? CreateRequestStatus.ALREADY_SENT
                            : CreateRequestStatus.REQUESTER_BUSY,
                    requesterOutgoing
            );
        }

        if (incomingByTarget.containsKey(requesterId)) {
            return new CreateRequestResult(CreateRequestStatus.REQUESTER_BUSY, null);
        }

        PendingDuelRequest targetIncoming = incomingByTarget.get(targetId);
        if (targetIncoming != null) {
            return new CreateRequestResult(
                    targetIncoming.requesterId().equals(requesterId)
                            ? CreateRequestStatus.ALREADY_SENT
                            : CreateRequestStatus.TARGET_BUSY,
                    targetIncoming
            );
        }

        if (outgoingByRequester.containsKey(targetId)) {
            return new CreateRequestResult(CreateRequestStatus.TARGET_BUSY, null);
        }

        SchedulerFacade.TaskHandle timeoutTask = schedulerFacade.runLater(() -> {
            PendingDuelRequest expired = removeInternal(requesterId, targetId, true);
            if (expired != null) {
                onExpire.accept(expired);
            }
        }, REQUEST_TIMEOUT_TICKS);

        PendingDuelRequest request = new PendingDuelRequest(
                requesterId,
                requesterName,
                targetId,
                targetName,
                mode,
                timeoutTask
        );
        outgoingByRequester.put(requesterId, request);
        incomingByTarget.put(targetId, request);
        return new CreateRequestResult(CreateRequestStatus.CREATED, request);
    }

    public ResolveRequestResult resolveForTarget(UUID targetId, String requesterNameFilter) {
        PendingDuelRequest request = incomingByTarget.get(targetId);
        if (request == null) {
            return new ResolveRequestResult(ResolveRequestStatus.NOT_FOUND, null);
        }
        if (requesterNameFilter != null && !request.requesterName().equalsIgnoreCase(requesterNameFilter)) {
            return new ResolveRequestResult(ResolveRequestStatus.NOT_FOUND, null);
        }

        PendingDuelRequest removed = remove(request);
        return removed == null
                ? new ResolveRequestResult(ResolveRequestStatus.NOT_FOUND, null)
                : new ResolveRequestResult(ResolveRequestStatus.RESOLVED, removed);
    }

    public List<PendingDuelRequest> clearForPlayer(UUID playerId) {
        List<PendingDuelRequest> removed = new ArrayList<>();

        PendingDuelRequest outgoing = outgoingByRequester.get(playerId);
        if (outgoing != null) {
            PendingDuelRequest cleared = remove(outgoing);
            if (cleared != null) {
                removed.add(cleared);
            }
        }

        PendingDuelRequest incoming = incomingByTarget.get(playerId);
        if (incoming != null) {
            PendingDuelRequest cleared = remove(incoming);
            if (cleared != null && removed.stream().noneMatch(existing -> sameRequest(existing, cleared))) {
                removed.add(cleared);
            }
        }

        return List.copyOf(removed);
    }

    public void clearAll() {
        List<PendingDuelRequest> requests = List.copyOf(incomingByTarget.values());
        incomingByTarget.clear();
        outgoingByRequester.clear();
        requests.forEach(request -> request.timeoutTask().cancel());
    }

    public PendingDuelRequest pendingForTarget(UUID targetId) {
        return incomingByTarget.get(targetId);
    }

    private PendingDuelRequest remove(PendingDuelRequest request) {
        return removeInternal(request.requesterId(), request.targetId(), true);
    }

    private PendingDuelRequest removeInternal(UUID requesterId, UUID targetId, boolean cancelTask) {
        PendingDuelRequest request = incomingByTarget.get(targetId);
        if (request == null || !request.requesterId().equals(requesterId)) {
            return null;
        }

        if (!incomingByTarget.remove(targetId, request)) {
            return null;
        }
        outgoingByRequester.remove(requesterId, request);
        if (cancelTask) {
            request.timeoutTask().cancel();
        }
        return request;
    }

    private boolean sameRequest(PendingDuelRequest first, PendingDuelRequest second) {
        return first.requesterId().equals(second.requesterId())
                && first.targetId().equals(second.targetId());
    }

    public enum CreateRequestStatus {
        CREATED,
        SELF_TARGET,
        ALREADY_SENT,
        REQUESTER_BUSY,
        TARGET_BUSY
    }

    public enum ResolveRequestStatus {
        RESOLVED,
        NOT_FOUND
    }

    public record CreateRequestResult(CreateRequestStatus status, PendingDuelRequest request) {
    }

    public record ResolveRequestResult(ResolveRequestStatus status, PendingDuelRequest request) {
    }

    public record PendingDuelRequest(
            UUID requesterId,
            String requesterName,
            UUID targetId,
            String targetName,
            DuelModeType mode,
            SchedulerFacade.TaskHandle timeoutTask
    ) {
    }
}
