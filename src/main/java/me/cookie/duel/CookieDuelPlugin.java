package me.cookie.duel;

import me.cookie.duel.command.CookieDuelCommand;
import me.cookie.duel.config.ConfigService;
import me.cookie.duel.duel.instance.InstanceCleanupService;
import me.cookie.duel.duel.instance.InstanceProvisionService;
import me.cookie.duel.duel.instance.WorldInstanceManager;
import me.cookie.duel.duel.instance.WorldTemplateManager;
import me.cookie.duel.duel.queue.QueueRegistry;
import me.cookie.duel.duel.service.AntiAbuseService;
import me.cookie.duel.duel.service.ConfirmService;
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
            getLogger().log(Level.SEVERE, "Failed to load CookieDuel configuration.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.schedulerFacade = new PaperSchedulerFacade(this);
        MessageService messageService = new MessageService(configService);
        QueueRegistry queueRegistry = new QueueRegistry(configService.queuesConfig().queues());
        DuelSessionManager duelSessionManager = new DuelSessionManager();
        AntiAbuseService antiAbuseService = new AntiAbuseService(configService);
        MatchmakingService matchmakingService = new MatchmakingService(queueRegistry, duelSessionManager, antiAbuseService);
        ConfirmService confirmService = new ConfirmService(schedulerFacade);
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
                queueRegistry,
                duelSessionManager,
                matchmakingService,
                confirmService,
                teleportCoordinator,
                snapshotService,
                antiAbuseService,
                wildLocationService,
                instanceProvisionService,
                instanceCleanupService,
                getLogger()
        );

        registerCommand(new CookieDuelCommand(configService, duelLifecycleService, messageService, schedulerFacade));
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(duelLifecycleService), this);
        getServer().getPluginManager().registerEvents(new DuelCombatListener(duelSessionManager, duelLifecycleService), this);
        getServer().getPluginManager().registerEvents(new InstanceProtectionListener(duelLifecycleService), this);

        if (configService.mainConfig().modes().arenaInstance().cleanupLeftoversOnStartup()) {
            duelLifecycleService.cleanupLeftoverInstances().whenComplete((count, throwable) -> {
                if (throwable != null) {
                    getLogger().log(Level.WARNING, "Failed to cleanup leftover duel instances on startup.", throwable);
                    return;
                }
                getLogger().info("CookieDuel startup cleanup removed " + count + " leftover instance worlds.");
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
        getLogger().info("CookieDuel enabled. Version: " + getPluginMeta().getVersion());
        String schedulerMode = schedulerFacade != null && schedulerFacade.isFolia()
                ? "Folia runtime detected"
                : "Paper global/entity scheduler mode";
        getLogger().info("Scheduler mode: " + schedulerMode);
        getLogger().info("WILD enabled: " + configService.mainConfig().modes().wildEnabled());
        getLogger().info("ARENA_INSTANCE enabled: " + configService.mainConfig().modes().arenaInstance().enabled());
        getLogger().info("Configured WILD world: " + configService.worldsConfig().wild().world()
                + " (must already be loaded; random radius origin = world spawn)");
        getLogger().info("WILD world loaded now: " + (Bukkit.getWorld(configService.worldsConfig().wild().world()) != null));
        getLogger().info("blacklist.yml loaded: true"
                + " (floor=" + configService.blacklistConfig().wild().floorBlacklist().size()
                + ", body=" + configService.blacklistConfig().wild().bodyBlacklist().size()
                + ", nearby=" + configService.blacklistConfig().wild().nearbyBlacklist().size()
                + ", invalidEntries=" + configService.blacklistConfig().invalidEntryCount()
                + ")");
        if (configService.blacklistConfig().invalidEntryCount() > 0) {
            getLogger().warning("blacklist.yml contained invalid entries. See earlier warnings for the exact values.");
        }
        getLogger().info("ARENA_INSTANCE cleanup on startup: "
                + configService.mainConfig().modes().arenaInstance().cleanupLeftoversOnStartup());
        getLogger().info("ARENA_INSTANCE template folders are validated from the server world container on startup.");
        getLogger().info("Configured queues: " + configService.queuesConfig().queues().size());
        getLogger().info("Configured arena templates: " + configService.worldsConfig().arenaTemplates().size());
    }
}
