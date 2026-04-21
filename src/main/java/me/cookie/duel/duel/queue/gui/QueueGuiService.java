package me.cookie.duel.duel.queue.gui;

import me.cookie.duel.duel.DuelModeType;
import me.cookie.duel.duel.queue.PlayerQueueEntry;
import me.cookie.duel.duel.service.DuelLifecycleService;
import me.cookie.duel.message.MessageService;
import me.cookie.duel.player.PlayerProfileService;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueGuiService {

    private static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final int GUI_SIZE = 54;

    private final DuelLifecycleService duelLifecycleService;
    private final MessageService messageService;
    private final PlayerProfileService playerProfileService;
    private final SchedulerFacade schedulerFacade;
    private final Map<UUID, Instant> refreshCooldownUntil = new ConcurrentHashMap<>();

    public QueueGuiService(DuelLifecycleService duelLifecycleService,
                           MessageService messageService,
                           PlayerProfileService playerProfileService,
                           SchedulerFacade schedulerFacade) {
        this.duelLifecycleService = duelLifecycleService;
        this.messageService = messageService;
        this.playerProfileService = playerProfileService;
        this.schedulerFacade = schedulerFacade;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int requestedPage) {
        schedulerFacade.runForEntity(player, () -> player.openInventory(buildInventory(player, requestedPage)));
    }

    public void refresh(Player player, QueueGuiHolder holder) {
        Instant now = Instant.now();
        Instant cooldownUntil = refreshCooldownUntil.get(player.getUniqueId());
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
            long seconds = Math.max(1L, cooldownUntil.getEpochSecond() - now.getEpochSecond());
            messageService.send(player, "queue.gui-refresh-cooldown", Map.of("seconds", String.valueOf(seconds)));
            return;
        }

        refreshCooldownUntil.put(player.getUniqueId(), now.plusSeconds(5L));
        open(player, holder.page());
    }

    public void handleEntryClick(Player player, String queueId) {
        schedulerFacade.runForEntity(player, player::closeInventory);
        duelLifecycleService.joinQueueEntry(player, queueId);
    }

    private Inventory buildInventory(Player viewer, int requestedPage) {
        List<PlayerQueueEntry> entries = duelLifecycleService.activeQueueEntriesForCurrentMode();
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) CONTENT_SLOTS.size()));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int startIndex = page * CONTENT_SLOTS.size();
        int endIndex = Math.min(entries.size(), startIndex + CONTENT_SLOTS.size());

        Map<Integer, String> queueIdsBySlot = new LinkedHashMap<>();
        QueueGuiHolder holder = new QueueGuiHolder(page, queueIdsBySlot);
        Inventory inventory = Bukkit.createInventory(
                holder,
                GUI_SIZE,
                messageService.renderRaw("gui.queue-browser.title", Map.of(
                        "mode", duelLifecycleService.activeModeDisplayName()
                ))
        );
        holder.attach(inventory);

        fillBorder(inventory);
        addControls(inventory);
        inventory.setItem(QueueGuiHolder.PROFILE_SLOT, createProfileItem(viewer));

        int contentIndex = 0;
        for (int entryIndex = startIndex; entryIndex < endIndex; entryIndex++) {
            PlayerQueueEntry entry = entries.get(entryIndex);
            int slot = CONTENT_SLOTS.get(contentIndex++);
            queueIdsBySlot.put(slot, entry.id());
            inventory.setItem(slot, createQueueItem(entry));
        }

        return inventory;
    }

    private void fillBorder(Inventory inventory) {
        ItemStack border = createSimpleItem(
                Material.BLACK_STAINED_GLASS_PANE,
                messageService.renderRaw("gui.queue-browser.border-name", Map.of()),
                List.of()
        );

        for (int slot = 0; slot < GUI_SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) {
                inventory.setItem(slot, border);
            }
        }
    }

    private void addControls(Inventory inventory) {
        inventory.setItem(
                QueueGuiHolder.PREVIOUS_SLOT,
                createSimpleItem(
                        Material.ARROW,
                        messageService.renderRaw("gui.queue-browser.previous-name", Map.of()),
                        messageService.renderRawList("gui.queue-browser.previous-lore", Map.of())
                )
        );
        inventory.setItem(
                QueueGuiHolder.NEXT_SLOT,
                createSimpleItem(
                        Material.ARROW,
                        messageService.renderRaw("gui.queue-browser.next-name", Map.of()),
                        messageService.renderRawList("gui.queue-browser.next-lore", Map.of())
                )
        );
        inventory.setItem(
                QueueGuiHolder.REFRESH_SLOT,
                createSimpleItem(
                        Material.BEACON,
                        messageService.renderRaw("gui.queue-browser.refresh-name", Map.of()),
                        messageService.renderRawList("gui.queue-browser.refresh-lore", Map.of())
                )
        );
    }

    private ItemStack createQueueItem(PlayerQueueEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.ownerId());
        meta.setOwningPlayer(offlinePlayer);
        meta.setDisplayName(messageService.renderRaw("gui.queue-browser.entry-name", Map.of("owner", entry.ownerName())));
        meta.setLore(messageService.renderRawList("gui.queue-browser.entry-lore", Map.of(
                "owner", entry.ownerName(),
                "mode", displayMode(entry.mode()),
                "money", entry.money()
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProfileItem(Player viewer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setOwningPlayer(viewer);
        meta.setDisplayName(messageService.renderRaw("gui.queue-browser.profile-name", Map.of("player", viewer.getName())));
        meta.setLore(messageService.renderRawList("gui.queue-browser.profile-lore", Map.of(
                "player", viewer.getName(),
                "money", playerProfileService.money(viewer),
                "kills", String.valueOf(playerProfileService.kills(viewer)),
                "deaths", String.valueOf(playerProfileService.deaths(viewer)),
                "points", playerProfileService.points(viewer),
                "mode", duelLifecycleService.activeModeDisplayName()
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String displayMode(DuelModeType mode) {
        return messageService.renderRaw("modes." + mode.langKey(), Map.of());
    }

    private ItemStack createSimpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(name);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
