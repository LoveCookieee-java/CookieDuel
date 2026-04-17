package me.cookie.duel.listener;

import me.cookie.duel.duel.service.DuelLifecycleService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class InstanceProtectionListener implements Listener {

    private final DuelLifecycleService duelLifecycleService;

    public InstanceProtectionListener(DuelLifecycleService duelLifecycleService) {
        this.duelLifecycleService = duelLifecycleService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!duelLifecycleService.canBreakBlocks(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!duelLifecycleService.canPlaceBlocks(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
