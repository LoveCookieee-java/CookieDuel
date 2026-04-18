package me.cookie.duel.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.cookie.duel.CookieDuelPlugin;
import me.cookie.duel.duel.DuelModeType;
import me.cookie.duel.duel.service.DuelLifecycleService;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public final class CookieDuelPlaceholderExpansion extends PlaceholderExpansion {

    private final CookieDuelPlugin plugin;
    private final DuelLifecycleService duelLifecycleService;

    public CookieDuelPlaceholderExpansion(CookieDuelPlugin plugin, DuelLifecycleService duelLifecycleService) {
        this.plugin = plugin;
        this.duelLifecycleService = duelLifecycleService;
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getIdentifier() {
        return "CD";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) {
            return null;
        }

        return switch (params.toUpperCase(Locale.ROOT)) {
            case "WILD" -> String.valueOf(duelLifecycleService.activeQueueEntries().stream()
                    .filter(entry -> entry.mode() == DuelModeType.WILD)
                    .count());
            case "ARENA", "ARENA_INSTANCE" -> String.valueOf(duelLifecycleService.activeQueueEntries().stream()
                    .filter(entry -> entry.mode() == DuelModeType.ARENA)
                    .count());
            default -> null;
        };
    }
}
