package me.cookie.duel.command;

import me.cookie.duel.config.ConfigService;
import me.cookie.duel.duel.DuelModeType;
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

    private final ConfigService configService;
    private final DuelLifecycleService duelLifecycleService;
    private final QueueGuiService queueGuiService;
    private final MessageService messageService;
    private final SchedulerFacade schedulerFacade;

    public CookieDuelCommand(ConfigService configService,
                             DuelLifecycleService duelLifecycleService,
                             QueueGuiService queueGuiService,
                             MessageService messageService,
                             SchedulerFacade schedulerFacade) {
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
            case "queue" -> handleQueue(player, args);
            case "random" -> {
                duelLifecycleService.joinRandomQueueEntry(player);
                yield true;
            }
            case "leave" -> {
                duelLifecycleService.leaveQueue(player);
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
        if (args.length != 2) {
            messageService.send(player, "queue.create-usage");
            return true;
        }

        DuelModeType mode = parseMode(args[1]);
        if (mode == null) {
            messageService.send(player, "general.invalid-mode");
            return true;
        }

        duelLifecycleService.createQueueEntry(player, mode);
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
            suggestions.add("queue");
            suggestions.add("list");
            suggestions.add("random");
            suggestions.add("leave");
            suggestions.add("surrender");
            if (sender.hasPermission("cookieduel.admin")) {
                suggestions.add("admin");
            }
            return filter(suggestions, args[0]);
        }

        if (args.length == 2 && "queue".equalsIgnoreCase(args[0])) {
            suggestions.add("WILD");
            suggestions.add("ARENA_INSTANCE");
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
        sender.sendMessage("/cd queue <mode>");
        sender.sendMessage("/cd list");
        sender.sendMessage("/cd random");
        sender.sendMessage("/cd leave");
        sender.sendMessage("/cd surrender");
        sender.sendMessage("/cd admin reload");
        sender.sendMessage("/cd admin forcestop <player>");
        sender.sendMessage("/cd admin cleanupinstances");
        sender.sendMessage("Use /cd queue <mode> to open a queue under your player name, then /cd list to browse active entries.");
    }

    private DuelModeType parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return DuelModeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
