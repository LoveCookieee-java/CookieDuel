package me.cookie.duel.message;

import me.cookie.duel.config.ConfigService;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageService {

    private final ConfigService configService;

    public MessageService(ConfigService configService) {
        this.configService = configService;
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(render(path, Map.of()));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(render(path, placeholders));
    }

    public void send(Player player, String path, Map<String, String> placeholders) {
        player.sendMessage(render(path, placeholders));
    }

    public String render(String path, Map<String, String> placeholders) {
        FileConfiguration config = configService.messagesConfig();
        String raw = config.getString(path, path);
        String formatted = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return translateLegacyColors(formatted);
    }

    private String translateLegacyColors(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '&' && index + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(index + 1));
                if (isLegacyCode(next)) {
                    builder.append('\u00A7').append(next);
                    index++;
                    continue;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private boolean isLegacyCode(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'k' && value <= 'o')
                || value == 'r';
    }
}
