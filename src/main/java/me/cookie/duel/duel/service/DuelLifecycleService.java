package me.cookie.duel.duel.service;

import me.cookie.duel.config.ConfigService;
import me.cookie.duel.config.model.ArenaTemplateDefinition;
import me.cookie.duel.config.model.WildLocationSettings;
import me.cookie.duel.duel.DuelModeType;
import me.cookie.duel.duel.instance.InstanceCleanupService;
import me.cookie.duel.duel.instance.InstanceProvisionService;
import me.cookie.duel.duel.instance.ProvisionedArena;
import me.cookie.duel.duel.queue.PlayerQueueEntry;
import me.cookie.duel.duel.session.DuelSession;
import me.cookie.duel.duel.session.DuelSessionContext;
import me.cookie.duel.duel.session.DuelSessionManager;
import me.cookie.duel.duel.session.DuelSessionState;
import me.cookie.duel.duel.teleport.wild.WildLocationService;
import me.cookie.duel.duel.teleport.wild.WildSpawnPair;
import me.cookie.duel.message.MessageService;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DuelLifecycleService {

    private final ConfigService configService;
    private final MessageService messageService;
    private final SchedulerFacade schedulerFacade;
    private final DuelSessionManager duelSessionManager;
    private final MatchmakingService matchmakingService;
    private final TeleportCoordinator teleportCoordinator;
    private final SnapshotService snapshotService;
    private final AntiAbuseService antiAbuseService;
    private final WildLocationService wildLocationService;
    private final InstanceProvisionService instanceProvisionService;
    private final InstanceCleanupService instanceCleanupService;
    private final Logger logger;

    public DuelLifecycleService(ConfigService configService,
                                MessageService messageService,
                                SchedulerFacade schedulerFacade,
                                DuelSessionManager duelSessionManager,
                                MatchmakingService matchmakingService,
                                TeleportCoordinator teleportCoordinator,
                                SnapshotService snapshotService,
                                AntiAbuseService antiAbuseService,
                                WildLocationService wildLocationService,
                                InstanceProvisionService instanceProvisionService,
                                InstanceCleanupService instanceCleanupService,
                                Logger logger) {
        this.configService = configService;
        this.messageService = messageService;
        this.schedulerFacade = schedulerFacade;
        this.duelSessionManager = duelSessionManager;
        this.matchmakingService = matchmakingService;
        this.teleportCoordinator = teleportCoordinator;
        this.snapshotService = snapshotService;
        this.antiAbuseService = antiAbuseService;
        this.wildLocationService = wildLocationService;
        this.instanceProvisionService = instanceProvisionService;
        this.instanceCleanupService = instanceCleanupService;
        this.logger = logger;
    }

    public void createQueueEntry(Player player, String queueId, DuelModeType mode) {
        schedulerFacade.runSync(() -> {
            MatchmakingService.CreateQueueResult result = matchmakingService.createQueue(
                    player.getUniqueId(),
                    player.getName(),
                    queueId,
                    mode
            );
            switch (result.status()) {
                case CREATED -> sendToPlayer(player, "queue.created", Map.of(
                        "id", result.entry().id(),
                        "mode", result.entry().mode().name()
                ));
                case INVALID_ID -> sendToPlayer(player, "queue.create-usage", Map.of());
                case DUPLICATE_ID -> sendToPlayer(player, "queue.duplicate-id", Map.of());
                case ALREADY_OWN_QUEUE -> sendToPlayer(player, "queue.already-own", Map.of());
                case IN_SESSION -> sendToPlayer(player, "queue.in-session", Map.of());
                case JOIN_COOLDOWN -> sendToPlayer(player, "queue.join-cooldown", Map.of("seconds", String.valueOf(result.secondsRemaining())));
                case LEAVE_PENALTY -> sendToPlayer(player, "queue.leave-penalty", Map.of("seconds", String.valueOf(result.secondsRemaining())));
                case MODE_DISABLED -> sendToPlayer(player, "queue.disabled-mode", Map.of());
                case NO_ARENA_TEMPLATE -> sendToPlayer(player, "queue.no-default-arena-template", Map.of());
            }
        });
    }

    public void joinQueueEntry(Player player, String queueId) {
        schedulerFacade.runSync(() -> joinQueueEntryInternal(player, queueId));
    }

    public void joinRandomQueueEntry(Player player) {
        schedulerFacade.runSync(() -> {
            if (duelSessionManager.isInSession(player.getUniqueId())) {
                sendToPlayer(player, "queue.in-session", Map.of());
                return;
            }
            if (matchmakingService.ownsQueue(player.getUniqueId())) {
                sendToPlayer(player, "queue.already-own", Map.of());
                return;
            }

            List<PlayerQueueEntry> candidates = matchmakingService.randomJoinCandidates(player.getUniqueId());
            if (candidates.isEmpty()) {
                sendToPlayer(player, "queue.random-none", Map.of());
                return;
            }

            PlayerQueueEntry selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            sendToPlayer(player, "queue.random-joining", Map.of(
                    "id", selected.id(),
                    "owner", selected.ownerName(),
                    "mode", selected.mode().name()
            ));
            joinQueueEntryInternal(player, selected.id());
        });
    }

    public void leaveQueue(Player player) {
        schedulerFacade.runSync(() -> {
            if (matchmakingService.leaveQueue(player.getUniqueId())) {
                sendToPlayer(player, "queue.removed", Map.of());
                return;
            }
            sendToPlayer(player, "queue.not-in-queue", Map.of());
        });
    }

    public void surrender(Player player) {
        schedulerFacade.runSync(() -> {
            DuelSessionContext context = duelSessionManager.byPlayer(player.getUniqueId()).orElse(null);
            if (context == null) {
                sendToPlayer(player, "duel.no-session", Map.of());
                return;
            }

            DuelSessionState state = context.session().state();
            if (state == DuelSessionState.FIGHTING) {
                sendToPlayer(player, "duel.surrendered", Map.of());
                completeSessionWithWinner(context.session(), context.session().opponentOf(player.getUniqueId()));
                return;
            }

            cancelSession(context.session(), "surrendered");
        });
    }

    public void handleDisconnect(Player player) {
        schedulerFacade.runSync(() -> {
            if (matchmakingService.removeOwnedQueue(player.getUniqueId()) != null) {
                return;
            }

            duelSessionManager.byPlayer(player.getUniqueId()).ifPresent(context -> {
                if (context.session().state() == DuelSessionState.FIGHTING) {
                    completeSessionWithWinner(context.session(), context.session().opponentOf(player.getUniqueId()));
                    return;
                }
                cancelSession(context.session(), player.getName() + " left");
            });
        });
    }

    public void handleLethalDamage(Player victim) {
        schedulerFacade.runSync(() -> {
            DuelSessionContext context = duelSessionManager.byPlayer(victim.getUniqueId()).orElse(null);
            if (context == null || context.session().state() != DuelSessionState.FIGHTING) {
                return;
            }
            completeSessionWithWinner(context.session(), context.session().opponentOf(victim.getUniqueId()));
        });
    }

    public void forceStop(Player target) {
        schedulerFacade.runSync(() ->
                duelSessionManager.byPlayer(target.getUniqueId()).ifPresent(context -> cancelSession(context.session(), "stopped by admin"))
        );
    }

    public void reloadQueues() {
        // Player-created queues live in memory and do not need config reload handling.
    }

    public CompletableFuture<Integer> cleanupLeftoverInstances() {
        return instanceCleanupService.cleanupLeftoverInstances();
    }

    public void applyPendingReturn(Player player) {
        snapshotService.applyPendingReturn(player);
    }

    public void shutdown() {
        for (DuelSessionContext context : duelSessionManager.activeContexts()) {
            cancelSession(context.session(), "server shutting down");
        }
        matchmakingService.clearQueues();
    }

    public List<PlayerQueueEntry> activeQueueEntries() {
        return matchmakingService.activeEntries();
    }

    private void handleMatchFound(DuelSessionContext context) {
        Player first = Bukkit.getPlayer(context.session().firstPlayer());
        Player second = Bukkit.getPlayer(context.session().secondPlayer());
        if (first == null || second == null) {
            cancelSession(context.session(), "player went offline");
            return;
        }

        beginProvisioning(context);
    }

    private void joinQueueEntryInternal(Player player, String queueId) {
        MatchmakingService.JoinQueueResult result = matchmakingService.joinQueue(player.getUniqueId(), queueId);
        switch (result.status()) {
            case MATCH_FOUND -> {
                sendToPlayer(player, "queue.join-started", Map.of("id", result.entry().id()));
                handleMatchFound(result.sessionContext());
            }
            case NOT_FOUND -> sendToPlayer(player, "queue.not-found", Map.of("id", queueId));
            case OWN_QUEUE -> sendToPlayer(player, "queue.own-entry", Map.of());
            case OWNER_UNAVAILABLE -> sendToPlayer(player, "queue.owner-offline", Map.of());
            case ALREADY_OWN_QUEUE -> sendToPlayer(player, "queue.already-own", Map.of());
            case IN_SESSION -> sendToPlayer(player, "queue.in-session", Map.of());
            case JOIN_COOLDOWN -> sendToPlayer(player, "queue.join-cooldown", Map.of("seconds", String.valueOf(result.secondsRemaining())));
            case LEAVE_PENALTY -> sendToPlayer(player, "queue.leave-penalty", Map.of("seconds", String.valueOf(result.secondsRemaining())));
            case REMATCH_BLOCKED -> sendToPlayer(player, "queue.rematch-blocked", Map.of());
            case MODE_DISABLED -> sendToPlayer(player, "queue.disabled-mode", Map.of());
            case NO_ARENA_TEMPLATE -> sendToPlayer(player, "queue.no-default-arena-template", Map.of());
        }
    }

    private void beginProvisioning(DuelSessionContext context) {
        DuelSession session = context.session();
        if (!session.transition(DuelSessionState.MATCH_FOUND, DuelSessionState.PROVISIONING)) {
            return;
        }

        Player first = Bukkit.getPlayer(session.firstPlayer());
        Player second = Bukkit.getPlayer(session.secondPlayer());
        if (first == null || second == null) {
            cancelSession(session, "player went offline");
            return;
        }

        CompletableFuture<PlayerSnapshot> firstSnapshotFuture = snapshotService.capture(first);
        CompletableFuture<PlayerSnapshot> secondSnapshotFuture = snapshotService.capture(second);

        CompletableFuture.allOf(firstSnapshotFuture, secondSnapshotFuture)
                .whenComplete((ignored, throwable) -> schedulerFacade.runSync(() -> {
                    if (throwable != null) {
                        logger.log(Level.WARNING,
                                "Could not capture player return locations for session " + session.sessionId() + ".",
                                throwable);
                        cancelSession(session, "setup failed");
                        return;
                    }

                    context.setFirstSnapshot(firstSnapshotFuture.join());
                    context.setSecondSnapshot(secondSnapshotFuture.join());
                    sendToParticipants(context, "duel.preparing", Map.of());

                    if (session.mode() == DuelModeType.WILD) {
                        provisionWild(context);
                        return;
                    }

                    provisionArenaInstance(context);
                }));
    }

    private void provisionWild(DuelSessionContext context) {
        WildLocationSettings settings = configService.worldsConfig().wild();
        World world = Bukkit.getWorld(settings.world());
        if (world == null) {
            logger.warning("WILD session " + context.session().sessionId()
                    + " could not continue because world '" + settings.world() + "' is no longer loaded.");
            cancelSession(context.session(), "wild world is unavailable");
            return;
        }

        wildLocationService.findSpawnPair(
                        schedulerFacade,
                        world,
                        settings,
                        configService.blacklistConfig().wild(),
                        Set.of(context.session().firstPlayer(), context.session().secondPlayer())
                )
                .whenComplete((pairOptional, throwable) -> schedulerFacade.runSync(() -> {
                    if (throwable != null) {
                        logger.log(Level.WARNING,
                                "Error while finding a WILD spawn pair for session " + context.session().sessionId() + ".",
                                throwable);
                        cancelSession(context.session(), "wild spawn setup failed");
                        return;
                    }

                    Optional<WildSpawnPair> pair = pairOptional;
                    if (pair.isEmpty()) {
                        logger.warning("Could not find a fair WILD spawn pair for session "
                                + context.session().sessionId()
                                + " in world '" + settings.world() + "' after "
                                + settings.maxAttempts()
                                + " tries from world spawn.");
                        sendToParticipants(context, "duel.no-safe-spawn", Map.of());
                        cancelSession(context.session(), "no safe spawn found");
                        return;
                    }

                    context.setFirstTarget(pair.get().spawnA());
                    context.setSecondTarget(pair.get().spawnB());
                    beginTeleport(context);
                }));
    }

    private void provisionArenaInstance(DuelSessionContext context) {
        DuelSession session = context.session();
        instanceProvisionService.provision(session)
                .orTimeout(configService.mainConfig().modes().arenaInstance().instanceCreateTimeoutSeconds(), TimeUnit.SECONDS)
                .whenComplete((arena, throwable) -> schedulerFacade.runSync(() -> {
                    if (throwable != null) {
                        logger.warning("Arena setup failed for session " + session.sessionId() + ": " + throwable.getMessage());
                        sendToParticipants(context, "duel.provision-failed", Map.of());
                        cancelSession(session, "arena setup failed");
                        return;
                    }

                    context.setInstanceWorldName(arena.instanceWorldName());
                    context.setFirstTarget(arena.spawnA());
                    context.setSecondTarget(arena.spawnB());
                    beginTeleport(context);
                }));
    }

    private void beginTeleport(DuelSessionContext context) {
        DuelSession session = context.session();
        if (!session.transition(DuelSessionState.PROVISIONING, DuelSessionState.TELEPORTING)) {
            return;
        }

        Player first = Bukkit.getPlayer(session.firstPlayer());
        Player second = Bukkit.getPlayer(session.secondPlayer());
        if (first == null || second == null || context.firstTarget() == null || context.secondTarget() == null) {
            cancelSession(session, "teleport setup failed");
            return;
        }

        sendToParticipants(context, "duel.teleporting", Map.of());
        Location firstFallback = context.firstSnapshot() == null ? null : context.firstSnapshot().returnLocation();

        teleportCoordinator.teleportBoth(
                        first,
                        context.firstTarget(),
                        firstFallback,
                        second,
                        context.secondTarget()
                )
                .whenComplete((teleportResult, throwable) -> schedulerFacade.runSync(() -> {
                    if (throwable != null) {
                        logger.log(Level.WARNING,
                                "Teleport step threw an exception for session " + session.sessionId() + ".",
                                throwable);
                        sendToParticipants(context, "duel.teleport-failed", Map.of());
                        cancelSession(session, "teleport step failed");
                        return;
                    }
                    if (teleportResult == null || !teleportResult.success()) {
                        logger.warning("Teleport failed for session "
                                + session.sessionId()
                                + ": "
                                + (teleportResult == null ? "unknown teleport result" : teleportResult.failureReason())
                                + " firstTeleported="
                                + (teleportResult != null && teleportResult.firstTeleported())
                                + " secondTeleported="
                                + (teleportResult != null && teleportResult.secondTeleported())
                                + " rollbackAttempted="
                                + (teleportResult != null && teleportResult.rollbackAttempted())
                                + " rollbackSucceeded="
                                + (teleportResult != null && teleportResult.rollbackSucceeded()));
                        sendToParticipants(context, "duel.teleport-failed", Map.of());
                        cancelSession(session, "teleport failed");
                        return;
                    }
                    startCountdown(context);
                }));
    }

    private void startCountdown(DuelSessionContext context) {
        long delayTicks = Math.max(0L, configService.mainConfig().duel().prestartCountdownSeconds() * 20L);
        if (delayTicks == 0L) {
            startFight(context);
            return;
        }

        context.setCountdownTask(schedulerFacade.runLater(() -> startFight(context), delayTicks));
    }

    private void startFight(DuelSessionContext context) {
        DuelSession session = context.session();
        if (!session.transition(DuelSessionState.TELEPORTING, DuelSessionState.FIGHTING)) {
            return;
        }

        sendToParticipants(context, "duel.started", Map.of());

        long timeoutTicks = Math.max(0L, configService.mainConfig().duel().maxFightSeconds() * 20L);
        if (timeoutTicks > 0L) {
            context.setFightTimeoutTask(schedulerFacade.runLater(
                    () -> cancelSession(session, "time limit reached"),
                    timeoutTicks
            ));
        }
    }

    private void completeSessionWithWinner(DuelSession session, UUID winnerId) {
        DuelSessionContext context = duelSessionManager.bySessionId(session.sessionId()).orElse(null);
        if (context == null) {
            return;
        }
        if (!session.transition(DuelSessionState.FIGHTING, DuelSessionState.ENDING)) {
            return;
        }

        context.cancelTasks();

        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(session.opponentOf(winnerId));
        if (winner != null && loser != null) {
            if (session.mode() == DuelModeType.ARENA_INSTANCE) {
                sendToPlayer(winner, "duel.arena-won", Map.of("opponent", loser.getName()));
                sendToPlayer(loser, "duel.arena-lost", Map.of("opponent", winner.getName()));
                showArenaResultTitle(winner, "duel.arena-title-win-title", "duel.arena-title-win-subtitle", Map.of("opponent", loser.getName()));
                showArenaResultTitle(loser, "duel.arena-title-lose-title", "duel.arena-title-lose-subtitle", Map.of("opponent", winner.getName()));
            } else {
                sendToPlayer(winner, "duel.won", Map.of("opponent", loser.getName()));
                sendToPlayer(loser, "duel.lost", Map.of("opponent", winner.getName()));
            }
        } else if (winner != null) {
            sendToPlayer(winner, session.mode() == DuelModeType.ARENA_INSTANCE ? "duel.arena-won" : "duel.won", Map.of("opponent", "opponent"));
        }

        cleanupSession(context);
    }

    private void cancelSession(DuelSession session, String reason) {
        DuelSessionContext context = duelSessionManager.bySessionId(session.sessionId()).orElse(null);
        if (context == null) {
            return;
        }

        DuelSessionState stateBeforeCancel = session.state();
        boolean transitioned = session.transitionAny(
                Set.of(
                        DuelSessionState.MATCH_FOUND,
                        DuelSessionState.PROVISIONING,
                        DuelSessionState.TELEPORTING,
                        DuelSessionState.FIGHTING
                ),
                stateBeforeCancel == DuelSessionState.FIGHTING ? DuelSessionState.ENDING : DuelSessionState.CANCELLED
        );
        if (!transitioned) {
            return;
        }

        logger.info("Cancelling session "
                + session.sessionId()
                + " from state "
                + stateBeforeCancel
                + ": "
                + reason);
        context.cancelTasks();
        sendToParticipants(context, "duel.cancelled", Map.of("reason", reason));
        cleanupSession(context);
    }

    private void cleanupSession(DuelSessionContext context) {
        DuelSession session = context.session();
        if (!context.beginCleanup()) {
            return;
        }

        session.transitionAny(
                Set.of(DuelSessionState.ENDING, DuelSessionState.CANCELLED, DuelSessionState.PROVISIONING, DuelSessionState.TELEPORTING, DuelSessionState.MATCH_FOUND),
                DuelSessionState.CLEANUP
        );

        boolean returnPlayersAfterDuel = context.instanceWorldName() != null;
        CompletableFuture<Void> firstReturn = returnPlayersAfterDuel
                ? snapshotService.returnOrQueue(session.firstPlayer(), context.firstSnapshot(), null)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<Void> secondReturn = returnPlayersAfterDuel
                ? snapshotService.returnOrQueue(session.secondPlayer(), context.secondSnapshot(), null)
                : CompletableFuture.completedFuture(null);

        CompletableFuture.allOf(firstReturn, secondReturn)
                .exceptionally(throwable -> null)
                .thenCompose(ignored -> {
                    if (context.instanceWorldName() == null) {
                        return CompletableFuture.completedFuture(true);
                    }
                    return instanceCleanupService.cleanupInstance(context.instanceWorldName())
                            .orTimeout(configService.mainConfig().modes().arenaInstance().instanceDeleteTimeoutSeconds(), TimeUnit.SECONDS)
                            .exceptionally(throwable -> false);
                }).whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        logger.log(Level.WARNING,
                                "Cleanup hit an error for session " + session.sessionId() + ".",
                                throwable);
                    }
                    antiAbuseService.recordSessionEnd(session.firstPlayer(), session.secondPlayer());
                    duelSessionManager.remove(session);
                });
    }

    private void sendToParticipants(DuelSessionContext context, String path, Map<String, String> placeholders) {
        sendToPlayer(context.session().firstPlayer(), path, placeholders);
        sendToPlayer(context.session().secondPlayer(), path, placeholders);
    }

    private void sendToPlayer(UUID playerId, String path, Map<String, String> placeholders) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        sendToPlayer(player, path, placeholders);
    }

    private void sendToPlayer(Player player, String path, Map<String, String> placeholders) {
        schedulerFacade.runForEntity(player, () -> messageService.send(player, path, placeholders));
    }

    private void showArenaResultTitle(Player player,
                                      String titlePath,
                                      String subtitlePath,
                                      Map<String, String> placeholders) {
        schedulerFacade.runForEntity(player, () -> player.sendTitle(
                messageService.renderRaw(titlePath, placeholders),
                messageService.renderRaw(subtitlePath, placeholders),
                6,
                60,
                16
        ));
    }

    public boolean canBreakBlocks(Player player) {
        return arenaPermission(player, true);
    }

    public boolean canPlaceBlocks(Player player) {
        return arenaPermission(player, false);
    }

    private boolean arenaPermission(Player player, boolean blockBreak) {
        DuelSessionContext context = duelSessionManager.byPlayer(player.getUniqueId()).orElse(null);
        if (context == null || context.session().mode() != DuelModeType.ARENA_INSTANCE) {
            return true;
        }
        if (context.instanceWorldName() == null || !context.instanceWorldName().equals(player.getWorld().getName())) {
            return true;
        }

        ArenaTemplateDefinition template = configService.worldsConfig().arenaTemplates().get(context.session().templateId());
        if (template == null) {
            return true;
        }
        return blockBreak ? template.settings().allowBlockBreak() : template.settings().allowBlockPlace();
    }
}
