package me.cookie.duel.duel.queue;

import me.cookie.duel.duel.DuelModeType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PlayerQueueEntry {

    private final String id;
    private final UUID ownerId;
    private final String ownerName;
    private final DuelModeType mode;
    private final String money;
    private final Instant createdAt;
    private volatile boolean active;

    public PlayerQueueEntry(String id,
                            UUID ownerId,
                            String ownerName,
                            DuelModeType mode,
                            String money,
                            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.money = Objects.requireNonNull(money, "money");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.active = true;
    }

    public String id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public DuelModeType mode() {
        return mode;
    }

    public String money() {
        return money;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean active() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }
}
