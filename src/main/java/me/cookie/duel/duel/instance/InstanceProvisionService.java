package me.cookie.duel.duel.instance;

import me.cookie.duel.config.ConfigService;
import me.cookie.duel.config.model.ArenaTemplateDefinition;
import me.cookie.duel.config.model.InstanceWorldSettings;
import me.cookie.duel.duel.session.DuelSession;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

public final class InstanceProvisionService {

    private final ConfigService configService;
    private final SchedulerFacade schedulerFacade;
    private final WorldTemplateManager worldTemplateManager;
    private final WorldInstanceManager worldInstanceManager;
    private final Logger logger;
    private final Set<String> activeInstanceWorlds = ConcurrentHashMap.newKeySet();

    public InstanceProvisionService(ConfigService configService,
                                    SchedulerFacade schedulerFacade,
                                    WorldTemplateManager worldTemplateManager,
                                    WorldInstanceManager worldInstanceManager,
                                    Logger logger) {
        this.configService = configService;
        this.schedulerFacade = schedulerFacade;
        this.worldTemplateManager = worldTemplateManager;
        this.worldInstanceManager = worldInstanceManager;
        this.logger = logger;
    }

    public CompletableFuture<ProvisionedArena> provision(DuelSession session) {
        ArenaTemplateDefinition template = configService.worldsConfig().arenaTemplates().get(session.templateId());
        if (template == null || !template.enabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Missing arena template '" + session.templateId() + "'."));
        }

        String instanceWorldName = worldTemplateManager.instanceWorldName(template, session.sessionId());
        if (!reserveInstance(instanceWorldName)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Arena instance limit reached."));
        }

        Path templatePath = worldTemplateManager.templatePath(template);
        logger.info("Creating arena instance '" + instanceWorldName + "' from template '" + template.templateWorldName() + "'.");

        return schedulerFacade.supplyAsync(() -> {
            try {
                worldInstanceManager.copyTemplate(templatePath, instanceWorldName);
                return instanceWorldName;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenCompose(name -> schedulerFacade.supplySync(() -> {
            try {
                World world = worldInstanceManager.loadWorld(name);
                applyTemplateSettings(world, template.settings());
                return new ProvisionedArena(
                        name,
                        template.spawnA().toLocation(world),
                        template.spawnB().toLocation(world)
                );
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        })).whenComplete((arena, throwable) -> {
            if (throwable == null) {
                return;
            }
            logger.warning("Could not create arena instance '" + instanceWorldName + "': " + throwable.getMessage());
            cleanupFailedProvision(instanceWorldName);
        });
    }

    public void release(String instanceWorldName) {
        activeInstanceWorlds.remove(instanceWorldName);
    }

    public boolean isActive(String instanceWorldName) {
        return activeInstanceWorlds.contains(instanceWorldName);
    }

    private boolean reserveInstance(String instanceWorldName) {
        synchronized (activeInstanceWorlds) {
            int limit = configService.mainConfig().modes().arenaInstance().maxActiveInstances();
            if (activeInstanceWorlds.size() >= limit) {
                return false;
            }
            return activeInstanceWorlds.add(instanceWorldName);
        }
    }

    private void applyTemplateSettings(World world, InstanceWorldSettings settings) {
        if (settings.autoSetGamerules()) {
            setBooleanGameRule(world, "doMobSpawning", false);
            setBooleanGameRule(world, "doWeatherCycle", false);
            setBooleanGameRule(world, "doDaylightCycle", false);
            world.setStorm(false);
            world.setThundering(false);
            world.setTime(6000L);
        }
        if (settings.borderSize() > 0.0D) {
            world.getWorldBorder().setSize(settings.borderSize());
        }
    }

    @SuppressWarnings({"unchecked", "removal"})
    private void setBooleanGameRule(World world, String ruleName, boolean value) {
        for (GameRule<?> rule : GameRule.values()) {
            if (!rule.getName().equalsIgnoreCase(ruleName)) {
                continue;
            }
            world.setGameRule((GameRule<Boolean>) rule, value);
            break;
        }
    }

    private void cleanupFailedProvision(String instanceWorldName) {
        schedulerFacade.supplySync(() -> {
            worldInstanceManager.unloadWorld(instanceWorldName);
            return null;
        }).thenCompose(ignored -> schedulerFacade.supplyAsync(() -> {
            try {
                worldInstanceManager.deleteWorldDirectory(instanceWorldName);
                return null;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        })).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logger.warning("Cleanup after failed arena setup also failed for '" + instanceWorldName + "': " + throwable.getMessage());
            }
            release(instanceWorldName);
        });
    }
}
