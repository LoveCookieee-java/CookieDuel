package me.cookie.duel.duel.service;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

public final class TeleportCoordinator {

    private final me.cookie.duel.scheduler.SchedulerFacade schedulerFacade;

    public TeleportCoordinator(me.cookie.duel.scheduler.SchedulerFacade schedulerFacade) {
        this.schedulerFacade = schedulerFacade;
    }

    public CompletableFuture<TeleportBatchResult> teleportBoth(Entity first,
                                                               Location firstLocation,
                                                               Location firstRollbackLocation,
                                                               Entity second,
                                                               Location secondLocation) {
        CompletableFuture<TeleportBatchResult> result = new CompletableFuture<>();

        first.setFallDistance(0.0F);
        second.setFallDistance(0.0F);

        schedulerFacade.teleport(first, firstLocation).whenComplete((firstSuccess, firstError) -> {
            if (firstError != null || !Boolean.TRUE.equals(firstSuccess)) {
                result.complete(TeleportBatchResult.failed(
                        false,
                        false,
                        false,
                        false,
                        failureMessage("first", firstError, firstSuccess)
                ));
                return;
            }

            schedulerFacade.teleport(second, secondLocation).whenComplete((secondSuccess, secondError) -> {
                if (secondError != null || !Boolean.TRUE.equals(secondSuccess)) {
                    rollback(first, firstRollbackLocation, failureMessage("second", secondError, secondSuccess))
                            .whenComplete((rollbackResult, rollbackError) -> {
                                if (rollbackError != null) {
                                    result.complete(TeleportBatchResult.failed(
                                            true,
                                            false,
                                            true,
                                            false,
                                            failureMessage("second", secondError, secondSuccess)
                                                    + " Rollback threw: " + rollbackError.getMessage()
                                    ));
                                    return;
                                }
                                result.complete(rollbackResult);
                            });
                    return;
                }
                result.complete(TeleportBatchResult.successResult());
            });
        });

        return result;
    }

    private CompletableFuture<TeleportBatchResult> rollback(Entity player,
                                                            Location rollbackLocation,
                                                            String failureReason) {
        if (rollbackLocation == null) {
            return CompletableFuture.completedFuture(TeleportBatchResult.failed(
                    true,
                    false,
                    false,
                    false,
                    failureReason + " No rollback destination was available."
            ));
        }

        return schedulerFacade.teleport(player, rollbackLocation).handle((rollbackSuccess, rollbackError) -> {
            boolean success = rollbackError == null && Boolean.TRUE.equals(rollbackSuccess);
            String suffix = success
                    ? " Rollback to the safe destination succeeded."
                    : " Rollback to the safe destination failed.";
            return TeleportBatchResult.failed(true, false, true, success, failureReason + suffix);
        });
    }

    private String failureMessage(String playerLabel, Throwable error, Boolean successFlag) {
        if (error != null) {
            return "Teleport failed for " + playerLabel + " player: " + error.getMessage();
        }
        return "Teleport returned unsuccessful for " + playerLabel + " player (success=" + successFlag + ").";
    }

    public record TeleportBatchResult(
            boolean success,
            boolean firstTeleported,
            boolean secondTeleported,
            boolean rollbackAttempted,
            boolean rollbackSucceeded,
            String failureReason
    ) {

        public static TeleportBatchResult successResult() {
            return new TeleportBatchResult(true, true, true, false, false, "");
        }

        public static TeleportBatchResult failed(boolean firstTeleported,
                                                 boolean secondTeleported,
                                                 boolean rollbackAttempted,
                                                 boolean rollbackSucceeded,
                                                 String failureReason) {
            return new TeleportBatchResult(
                    false,
                    firstTeleported,
                    secondTeleported,
                    rollbackAttempted,
                    rollbackSucceeded,
                    failureReason
            );
        }
    }
}
