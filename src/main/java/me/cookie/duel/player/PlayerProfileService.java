package me.cookie.duel.player;

import me.cookie.duel.message.MessageService;
import me.cookie.duel.placeholder.PlaceholderSupport;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PlayerProfileService {

    private static final String MONEY_PLACEHOLDER = "%vault_eco_balance_formatted%";
    private static final String POINTS_PLACEHOLDER = "%playerpoints_points_formatted%";

    private final PlaceholderSupport placeholderSupport;
    private final MessageService messageService;

    public PlayerProfileService(PlaceholderSupport placeholderSupport, MessageService messageService) {
        this.placeholderSupport = placeholderSupport;
        this.messageService = messageService;
    }

    public String money(Player player) {
        return placeholderSupport.resolve(player, MONEY_PLACEHOLDER, unavailable());
    }

    public String points(Player player) {
        return placeholderSupport.resolve(player, POINTS_PLACEHOLDER, unavailable());
    }

    public int kills(Player player) {
        return player.getStatistic(Statistic.PLAYER_KILLS);
    }

    public int deaths(Player player) {
        return player.getStatistic(Statistic.DEATHS);
    }

    private String unavailable() {
        return messageService.renderRaw("gui.queue-browser.stat-fallback", Map.of());
    }
}
