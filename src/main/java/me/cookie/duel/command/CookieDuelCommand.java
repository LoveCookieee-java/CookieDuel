package me.cookie.duel.command;

import me.cookie.duel.config.ConfigService;
import me.cookie.duel.config.model.QueueDefinition;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CookieDuelCommand implements CommandExecutor, TabCompleter {

    private final ConfigService configService;
    private final DuelLifecycleService duelLifecycleService;
    private final MessageService messageService;
    private final SchedulerFacade schedulerFacade;

    public CookieDuelCommand(ConfigService configService,
                             DuelLifecycleService duelLifecycleService,
                             MessageService messageService,
                             SchedulerFacade schedulerFacade) {
        this.configService = configService;
        this.duelLifecycleService = duelLifecycleService;
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

        if ("queues".equals(subcommand)) {
            return handleQueues(sender);
        }

        if ("info".equals(subcommand)) {
            return handleQueueInfo(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use that command.");
            return true;
        }

        return switch (subcommand) {
            case "queue" -> handleQueue(player, args);
            case "leave" -> {
                duelLifecycleService.leaveQueue(player);
                yield true;
            }
            case "accept" -> {
                duelLifecycleService.accept(player);
                yield true;
            }
            case "deny" -> {
                duelLifecycleService.deny(player);
                yield true;
            }
            case "surrender" -> {
                duelLifecycleService.surrender(player);
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleQueue(Player player, String[] args) {
        if (args.length < 2) {
            messageService.send(player, "queue.usage");
            return true;
        }

        duelLifecycleService.joinQueue(player, args[1]);
        return true;
    }

    private boolean handleQueues(CommandSender sender) {
        List<QueueDefinition> enabledQueues = configService.queuesConfig().queues().values().stream()
                .filter(QueueDefinition::enabled)
                .sorted(Comparator.comparing(QueueDefinition::id, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (enabledQueues.isEmpty()) {
            messageService.send(sender, "queue.list-empty");
            return true;
        }

        messageService.send(sender, "queue.list-header");
        for (QueueDefinition queue : enabledQueues) {
            messageService.send(sender, "queue.list-entry", Map.of(
                    "queue", queue.id(),
                    "name", queue.displayName(),
                    "mode", queue.mode().name()
            ));
        }
        return true;
    }

    private boolean handleQueueInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/cookieduel info <queueId>");
            return true;
        }

        QueueDefinition queue = configService.queuesConfig().queues().get(args[1]);
        if (queue == null) {
            messageService.send(sender, "queue.info-missing", Map.of("queue", args[1]));
            return true;
        }

        messageService.send(sender, "queue.info-header", Map.of("queue", queue.id()));
        messageService.send(sender, "queue.info-name", Map.of("name", queue.displayName()));
        messageService.send(sender, "queue.info-mode", Map.of("mode", queue.mode().name()));
        messageService.send(sender, "queue.info-enabled", Map.of("enabled", queue.enabled() ? "Yes" : "No"));
        messageService.send(sender, "queue.info-confirm", Map.of("confirm", queue.confirmRequired() ? "Yes" : "No"));

        if (queue.templateId() != null && !queue.templateId().isBlank()) {
            messageService.send(sender, "queue.info-template", Map.of("template", queue.templateId()));
        }
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
                    sender.sendMessage("Reload failed: " + exception.getMessage());
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
                            : "Cleanup failed: " + throwable.getMessage());
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
            suggestions.add("queue");
            suggestions.add("queues");
            suggestions.add("info");
            suggestions.add("leave");
            suggestions.add("accept");
            suggestions.add("deny");
            suggestions.add("surrender");
            if (sender.hasPermission("cookieduel.admin")) {
                suggestions.add("admin");
            }
            return filter(suggestions, args[0]);
        }

        if (args.length == 2 && "queue".equalsIgnoreCase(args[0])) {
            configService.queuesConfig().queues().values().stream()
                    .filter(QueueDefinition::enabled)
                    .map(QueueDefinition::id)
                    .forEach(suggestions::add);
            return filter(suggestions, args[1]);
        }

        if (args.length == 2 && "info".equalsIgnoreCase(args[0])) {
            suggestions.addAll(configService.queuesConfig().queues().keySet());
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
        sender.sendMessage("/cookieduel queue <queueId>");
        sender.sendMessage("/cookieduel queues");
        sender.sendMessage("/cookieduel info <queueId>");
        sender.sendMessage("/cookieduel leave");
        sender.sendMessage("/cookieduel accept");
        sender.sendMessage("/cookieduel deny");
        sender.sendMessage("/cookieduel surrender");
        sender.sendMessage("/cookieduel admin reload");
        sender.sendMessage("/cookieduel admin forcestop <player>");
        sender.sendMessage("/cookieduel admin cleanupinstances");
        sender.sendMessage("Queue IDs are loaded from queues.yml. Use /cookieduel queues to list enabled queues.");
    }
}
