package me.cookie.duel.duel.queue.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public final class QueueGuiHolder implements InventoryHolder {

    public static final int PREVIOUS_SLOT = 45;
    public static final int REFRESH_SLOT = 49;
    public static final int NEXT_SLOT = 53;
    public static final int PROFILE_SLOT = 4;

    private final int page;
    private final Map<Integer, String> queueIdsBySlot;
    private Inventory inventory;

    public QueueGuiHolder(int page, Map<Integer, String> queueIdsBySlot) {
        this.page = page;
        this.queueIdsBySlot = queueIdsBySlot;
    }

    public int page() {
        return page;
    }

    public String queueIdAt(int slot) {
        return queueIdsBySlot.get(slot);
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
