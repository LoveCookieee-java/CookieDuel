package me.cookie.duel;

import me.cookie.duel.command.CookieDuelCommand;
import me.cookie.duel.config.ConfigService;
import me.cookie.duel.duel.instance.InstanceCleanupService;
import me.cookie.duel.duel.instance.InstanceProvisionService;
import me.cookie.duel.duel.instance.WorldInstanceManager;
import me.cookie.duel.duel.instance.WorldTemplateManager;
import me.cookie.duel.duel.queue.PlayerQueueRegistry;
import me.cookie.duel.duel.queue.gui.QueueGuiService;
import me.cookie.duel.duel.service.AntiAbuseService;
import me.cookie.duel.duel.service.DuelLifecycleService;
import me.cookie.duel.duel.service.MatchmakingService;
import me.cookie.duel.duel.service.SnapshotService;
import me.cookie.duel.duel.service.TeleportCoordinator;
import me.cookie.duel.duel.session.DuelSessionManager;
import me.cookie.duel.duel.teleport.wild.WildLocationService;
import me.cookie.duel.duel.teleport.wild.WildLocationValidator;
import me.cookie.duel.listener.DuelCombatListener;
import me.cookie.duel.listener.InstanceProtectionListener;
import me.cookie.duel.listener.PlayerLifecycleListener;
import me.cookie.duel.listener.QueueGuiListener;
import me.cookie.duel.message.MessageService;
import me.cookie.duel.scheduler.PaperSchedulerFacade;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CookieDuelPlugin extends JavaPlugin {

    private ConfigService configService;
    private DuelLifecycleService duelLifecycleService;
    private SchedulerFacade schedulerFacade;

    @Override
    public void onEnable() {
        try {
            this.configService = new ConfigService(this);
            configService.load();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Could not load CookieDuel config files.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.schedulerFacade = new PaperSchedulerFacade(this);
        MessageService messageService = new MessageService(configService);
        PlayerQueueRegistry playerQueueRegistry = new PlayerQueueRegistry();
        DuelSessionManager duelSessionManager = new DuelSessionManager();
        AntiAbuseService antiAbuseService = new AntiAbuseService(configService);
        MatchmakingService matchmakingService = new MatchmakingService(configService, playerQueueRegistry, duelSessionManager, antiAbuseService);
        TeleportCoordinator teleportCoordinator = new TeleportCoordinator(schedulerFacade);
        SnapshotService snapshotService = new SnapshotService(schedulerFacade);
        WildLocationService wildLocationService = new WildLocationService(new WildLocationValidator());
        WorldTemplateManager worldTemplateManager = new WorldTemplateManager();
        WorldInstanceManager worldInstanceManager = new WorldInstanceManager();
        InstanceProvisionService instanceProvisionService = new InstanceProvisionService(
                configService,
                schedulerFacade,
                worldTemplateManager,
                worldInstanceManager,
                getLogger()
        );
        InstanceCleanupService instanceCleanupService = new InstanceCleanupService(
                configService,
                schedulerFacade,
                worldTemplateManager,
                worldInstanceManager,
                instanceProvisionService,
                getLogger()
        );

        this.duelLifecycleService = new DuelLifecycleService(
                configService,
                messageService,
                schedulerFacade,
                duelSessionManager,
                matchmakingService,
                teleportCoordinator,
                snapshotService,
                antiAbuseService,
                wildLocationService,
                instanceProvisionService,
                instanceCleanupService,
                getLogger()
        );
        QueueGuiService queueGuiService = new QueueGuiService(duelLifecycleService, messageService, schedulerFacade);

        registerCommand(new CookieDuelCommand(configService, duelLifecycleService, queueGuiService, messageService, schedulerFacade));
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(duelLifecycleService), this);
        getServer().getPluginManager().registerEvents(new DuelCombatListener(duelSessionManager, duelLifecycleService), this);
        getServer().getPluginManager().registerEvents(new InstanceProtectionListener(duelLifecycleService), this);
        getServer().getPluginManager().registerEvents(new QueueGuiListener(queueGuiService), this);

        if (configService.mainConfig().modes().arenaInstance().cleanupLeftoversOnStartup()) {
            duelLifecycleService.cleanupLeftoverInstances().whenComplete((count, throwable) -> {
                if (throwable != null) {
                    getLogger().log(Level.WARNING, "Could not clean up leftover duel worlds on startup.", throwable);
                    return;
                }
                getLogger().info("Startup cleanup removed " + count + " leftover duel world(s).");
            });
        }

        logStartupSummary();
    }

    @Override
    public void onDisable() {
        if (duelLifecycleService != null) {
            duelLifecycleService.shutdown();
        }
    }

    private void registerCommand(CookieDuelCommand commandHandler) {
        PluginCommand command = getCommand("cookieduel");
        if (command == null) {
            throw new IllegalStateException("Command 'cookieduel' is not defined in plugin.yml");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    private void logStartupSummary() {
        getLogger().info("CookieDuel " + getPluginMeta().getVersion() + " enabled.");
        String schedulerMode = schedulerFacade != null && schedulerFacade.isFolia()
                ? "Folia runtime detected"
                : "Paper scheduler mode";
        getLogger().info("Scheduler: " + schedulerMode);
        getLogger().info("WILD mode: " + (configService.mainConfig().modes().wildEnabled() ? "enabled" : "disabled"));
        getLogger().info("ARENA_INSTANCE mode: " + (configService.mainConfig().modes().arenaInstance().enabled() ? "enabled" : "disabled"));
        getLogger().info("WILD world: " + configService.worldsConfig().wild().world()
                + " (must already be loaded, search origin = world spawn)");
        getLogger().info("WILD world loaded: " + (Bukkit.getWorld(configService.worldsConfig().wild().world()) != null));
        getLogger().info("blacklist.yml loaded"
                + " (floor=" + configService.blacklistConfig().wild().floorBlacklist().size()
                + ", body=" + configService.blacklistConfig().wild().bodyBlacklist().size()
                + ", nearby=" + configService.blacklistConfig().wild().nearbyBlacklist().size()
                + ", invalid=" + configService.blacklistConfig().invalidEntryCount()
                + ")");
        if (configService.blacklistConfig().invalidEntryCount() > 0) {
            getLogger().warning("blacklist.yml has invalid entries. See earlier warnings for the exact paths.");
        }
        getLogger().info("Arena cleanup on startup: "
                + configService.mainConfig().modes().arenaInstance().cleanupLeftoversOnStartup());
        getLogger().info("Arena templates are checked in the server world container.");
        getLogger().info("Queues are player-created in game with /cd queue <mode> and use the owner's player name as the queue id.");
        getLogger().info("Default arena template: " + configService.worldsConfig().defaultArenaTemplateId());
        getLogger().info("Arena templates loaded: " + configService.worldsConfig().arenaTemplates().size());
    }
}
