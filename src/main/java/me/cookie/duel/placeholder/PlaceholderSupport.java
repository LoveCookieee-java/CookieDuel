package me.cookie.duel.placeholder;

import org.bukkit.entity.Player;

public interface PlaceholderSupport {

    boolean isAvailable();

    String resolve(Player player, String placeholder, String fallback);
}
