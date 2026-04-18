package me.cookie.duel.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public final class PapiPlaceholderSupport implements PlaceholderSupport {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String resolve(Player player, String placeholder, String fallback) {
        if (player == null || placeholder == null || placeholder.isBlank()) {
            return fallback;
        }

        String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
        if (resolved == null || resolved.isBlank() || resolved.equals(placeholder)) {
            return fallback;
        }

        return resolved;
    }
}
