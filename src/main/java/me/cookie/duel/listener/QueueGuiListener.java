package me.cookie.duel.listener;

import me.cookie.duel.duel.queue.gui.QueueGuiHolder;
import me.cookie.duel.duel.queue.gui.QueueGuiService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class QueueGuiListener implements Listener {

    private final QueueGuiService queueGuiService;

    public QueueGuiListener(QueueGuiService queueGuiService) {
        this.queueGuiService = queueGuiService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof QueueGuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == QueueGuiHolder.PREVIOUS_SLOT) {
            queueGuiService.open(player, holder.page() - 1);
            return;
        }
        if (slot == QueueGuiHolder.NEXT_SLOT) {
            queueGuiService.open(player, holder.page() + 1);
            return;
        }
        if (slot == QueueGuiHolder.REFRESH_SLOT) {
            queueGuiService.refresh(player, holder);
            return;
        }

        String queueId = holder.queueIdAt(slot);
        if (queueId != null) {
            queueGuiService.handleEntryClick(player, queueId);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QueueGuiHolder) {
            event.setCancelled(true);
        }
    }
}
