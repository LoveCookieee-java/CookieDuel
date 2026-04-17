package me.cookie.duel.duel.instance;

import me.cookie.duel.config.ConfigService;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

public final class InstanceCleanupService {

    private final ConfigService configService;
    private final SchedulerFacade schedulerFacade;
    private final WorldTemplateManager worldTemplateManager;
    private final WorldInstanceManager worldInstanceManager;
    private final InstanceProvisionService instanceProvisionService;
    private final Logger logger;

    public InstanceCleanupService(ConfigService configService,
                                  SchedulerFacade schedulerFacade,
                                  WorldTemplateManager worldTemplateManager,
                                  WorldInstanceManager worldInstanceManager,
                                  InstanceProvisionService instanceProvisionService,
                                  Logger logger) {
        this.configService = configService;
        this.schedulerFacade = schedulerFacade;
        this.worldTemplateManager = worldTemplateManager;
        this.worldInstanceManager = worldInstanceManager;
        this.instanceProvisionService = instanceProvisionService;
        this.logger = logger;
    }

    public CompletableFuture<Boolean> cleanupInstance(String instanceWorldName) {
        logger.info("Cleaning up arena instance '" + instanceWorldName + "'.");
        return schedulerFacade.supplySync(() -> {
            worldInstanceManager.unloadWorld(instanceWorldName);
            return null;
        }).thenCompose(ignored -> attemptDelete(instanceWorldName, 0))
                .whenComplete((success, throwable) -> {
                    instanceProvisionService.release(instanceWorldName);
                    if (throwable != null) {
                        logger.warning("Could not clean arena instance '" + instanceWorldName + "': " + throwable.getMessage());
                    } else if (Boolean.FALSE.equals(success)) {
                        logger.warning("Arena instance '" + instanceWorldName + "' could not be deleted after retries.");
                    }
                });
    }

    public CompletableFuture<Integer> cleanupLeftoverInstances() {
        return schedulerFacade.supplyAsync(() -> {
            try {
                return worldTemplateManager.findLeftoverInstances(configService.worldsConfig());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenCompose(leftovers -> {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            for (String leftover : leftovers) {
                if (instanceProvisionService.isActive(leftover) || Bukkit.getWorld(leftover) != null) {
                    continue;
                }
                futures.add(cleanupInstance(leftover));
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> (int) futures.stream().filter(CompletableFuture::join).count());
        });
    }

    private CompletableFuture<Boolean> attemptDelete(String instanceWorldName, int attempt) {
        return schedulerFacade.supplyAsync(() -> {
            try {
                return worldInstanceManager.deleteWorldDirectory(instanceWorldName);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenCompose(success -> {
            if (success) {
                return CompletableFuture.completedFuture(true);
            }
            if (attempt >= 2) {
                return CompletableFuture.completedFuture(false);
            }

            CompletableFuture<Boolean> retryFuture = new CompletableFuture<>();
            schedulerFacade.runLater(() -> attemptDelete(instanceWorldName, attempt + 1)
                    .whenComplete((retrySuccess, throwable) -> {
                        if (throwable != null) {
                            retryFuture.completeExceptionally(throwable);
                            return;
                        }
                        retryFuture.complete(retrySuccess);
                    }), 20L);
            return retryFuture;
        });
    }
}
