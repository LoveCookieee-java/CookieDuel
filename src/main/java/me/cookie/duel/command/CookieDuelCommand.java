package me.cookie.duel.command;

import me.cookie.duel.CookieDuelPlugin;
import me.cookie.duel.config.ConfigurationException;
import me.cookie.duel.config.ConfigService;
import me.cookie.duel.duel.queue.gui.QueueGuiService;
import me.cookie.duel.duel.service.DuelLifecycleService;
import me.cookie.duel.message.MessageService;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CookieDuelCommand implements CommandExecutor, TabCompleter {

    private final CookieDuelPlugin plugin;
    private final ConfigService configService;
    private final DuelLifecycleService duelLifecycleService;
    private final QueueGuiService queueGuiService;
    private final MessageService messageService;
    private final SchedulerFacade schedulerFacade;

    public CookieDuelCommand(CookieDuelPlugin plugin,
                             ConfigService configService,
                             DuelLifecycleService duelLifecycleService,
                             QueueGuiService queueGuiService,
                             MessageService messageService,
                             SchedulerFacade schedulerFacade) {
        this.plugin = plugin;
        this.configService = configService;
        this.duelLifecycleService = duelLifecycleService;
        this.queueGuiService = queueGuiService;
        this.messageService = messageService;
        this.schedulerFacade = schedulerFacade;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("admin".equals(subcommand)) {
            return handleAdmin(sender, args);
        }

        if ("list".equals(subcommand)) {
            return handleList(sender);
        }

        if (!(sender instanceof Player player)) {
            messageService.send(sender, "general.player-only");
            return true;
        }

        return switch (subcommand) {
            case "duel" -> handleDuel(player, args);
            case "queue" -> handleQueue(player, args);
            case "random" -> {
                duelLifecycleService.joinRandomQueueEntry(player);
                yield true;
            }
            case "leave" -> {
                duelLifecycleService.leaveQueue(player);
                yield true;
            }
            case "accept" -> handleAccept(player, args);
            case "deny" -> handleDeny(player, args);
            case "out" -> {
                duelLifecycleService.out(player);
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleDuel(Player player, String[] args) {
        if (args.length != 2) {
            messageService.send(player, "challenge.duel-usage");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            messageService.send(player, "challenge.target-offline", Map.of("player", args[1]));
            return true;
        }

        duelLifecycleService.createDirectDuelRequest(player, target);
        return true;
    }

    private boolean handleQueue(Player player, String[] args) {
        if (args.length != 1) {
            messageService.send(player, "queue.create-usage");
            return true;
        }

        duelLifecycleService.createQueueEntry(player);
        return true;
    }

    private boolean handleAccept(Player player, String[] args) {
        if (args.length > 2) {
            messageService.send(player, "challenge.accept-usage");
            return true;
        }

        duelLifecycleService.accept(player, args.length == 2 ? args[1] : null);
        return true;
    }

    private boolean handleDeny(Player player, String[] args) {
        if (args.length > 2) {
            messageService.send(player, "challenge.deny-usage");
            return true;
        }

        duelLifecycleService.deny(player, args.length == 2 ? args[1] : null);
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "general.player-only");
            return true;
        }

        queueGuiService.open(player);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cookieduel.admin")) {
            messageService.send(sender, "admin.no-permission");
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        String adminSubcommand = args[1].toLowerCase(Locale.ROOT);
        switch (adminSubcommand) {
            case "reload" -> {
                try {
                    configService.load();
                    duelLifecycleService.reloadQueues();
                    messageService.send(sender, "admin.reload-done");
                } catch (Exception exception) {
                    messageService.send(sender, "admin.reload-failed", Map.of("reason", exception.getMessage()));
                    if (exception instanceof ConfigurationException) {
                        plugin.getLogger().severe(exception.getMessage());
                        Bukkit.getPluginManager().disablePlugin(plugin);
                    }
                }
                return true;
            }
            case "forcestop" -> {
                if (args.length < 3) {
                    return false;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    messageService.send(sender, "admin.forcestop-missing");
                    return true;
                }
                duelLifecycleService.forceStop(target);
                messageService.send(sender, "admin.forcestop-done", Map.of("player", target.getName()));
                return true;
            }
            case "cleanupinstances" -> {
                messageService.send(sender, "admin.cleanup-started");
                duelLifecycleService.cleanupLeftoverInstances().whenComplete((count, throwable) -> {
                    deliverToSender(sender, throwable == null
                            ? messageService.render("admin.cleanup-done", Map.of("count", String.valueOf(count)))
                            : messageService.render("admin.cleanup-failed", Map.of("reason", throwable.getMessage())));
                });
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("duel");
            suggestions.add("queue");
            suggestions.add("list");
            suggestions.add("random");
            suggestions.add("leave");
            suggestions.add("accept");
            suggestions.add("deny");
            suggestions.add("out");
            if (sender.hasPermission("cookieduel.admin")) {
                suggestions.add("admin");
            }
            return filter(suggestions, args[0]);
        }

        if (args.length == 2 && "duel".equalsIgnoreCase(args[0])) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(sender)) {
                    suggestions.add(onlinePlayer.getName());
                }
            }
            return filter(suggestions, args[1]);
        }

        if (args.length == 2 && ("accept".equalsIgnoreCase(args[0]) || "deny".equalsIgnoreCase(args[0])) && sender instanceof Player player) {
            String pendingRequesterName = duelLifecycleService.pendingRequesterName(player);
            if (pendingRequesterName != null) {
                suggestions.add(pendingRequesterName);
            }
            return filter(suggestions, args[1]);
        }

        if (sender.hasPermission("cookieduel.admin") && args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            suggestions.add("reload");
            suggestions.add("forcestop");
            suggestions.add("cleanupinstances");
            return filter(suggestions, args[1]);
        }

        if (sender.hasPermission("cookieduel.admin") && args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "forcestop".equalsIgnoreCase(args[1])) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                suggestions.add(onlinePlayer.getName());
            }
            return filter(suggestions, args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();
    }

    private void deliverToSender(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            schedulerFacade.runForEntity(player, () -> sender.sendMessage(message));
            return;
        }
        schedulerFacade.runSync(() -> sender.sendMessage(message));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/cd duel <player>");
        sender.sendMessage("/cd accept [player]");
        sender.sendMessage("/cd deny [player]");
        sender.sendMessage("/cd queue");
        sender.sendMessage("/cd list");
        sender.sendMessage("/cd random");
        sender.sendMessage("/cd leave");
        sender.sendMessage("/cd out");
        sender.sendMessage("/cd admin reload");
        sender.sendMessage("/cd admin forcestop <player>");
        sender.sendMessage("/cd admin cleanupinstances");
        sender.sendMessage("Use /cd queue to open a queue in the active config mode, or /cd duel <player> for a direct challenge.");
    }
}
