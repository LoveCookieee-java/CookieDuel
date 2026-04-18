package me.cookie.duel.placeholder;

import org.bukkit.entity.Player;

public final class NoopPlaceholderSupport implements PlaceholderSupport {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String resolve(Player player, String placeholder, String fallback) {
        return fallback;
    }
}
