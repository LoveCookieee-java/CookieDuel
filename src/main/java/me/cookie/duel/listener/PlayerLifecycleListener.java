package me.cookie.duel.listener;

import me.cookie.duel.duel.service.DuelLifecycleService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {

    private final DuelLifecycleService duelLifecycleService;

    public PlayerLifecycleListener(DuelLifecycleService duelLifecycleService) {
        this.duelLifecycleService = duelLifecycleService;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        duelLifecycleService.handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        duelLifecycleService.handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        duelLifecycleService.applyPendingReturn(event.getPlayer());
    }
}
