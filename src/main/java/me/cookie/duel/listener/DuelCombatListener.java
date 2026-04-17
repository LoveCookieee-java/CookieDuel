package me.cookie.duel.listener;

import me.cookie.duel.duel.service.DuelLifecycleService;
import me.cookie.duel.duel.session.DuelSessionManager;
import me.cookie.duel.duel.session.DuelSessionState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public final class DuelCombatListener implements Listener {

    private final DuelSessionManager duelSessionManager;
    private final DuelLifecycleService duelLifecycleService;

    public DuelCombatListener(DuelSessionManager duelSessionManager, DuelLifecycleService duelLifecycleService) {
        this.duelSessionManager = duelSessionManager;
        this.duelLifecycleService = duelLifecycleService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        var context = duelSessionManager.byPlayer(player.getUniqueId()).orElse(null);
        if (context == null) {
            return;
        }

        if (context.session().state() != DuelSessionState.FIGHTING) {
            event.setCancelled(true);
            return;
        }

        if (event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            duelLifecycleService.handleLethalDamage(player);
        }
    }
}
