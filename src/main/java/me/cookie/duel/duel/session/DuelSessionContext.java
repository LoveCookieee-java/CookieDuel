package me.cookie.duel.duel.session;

import me.cookie.duel.duel.service.PlayerSnapshot;
import me.cookie.duel.scheduler.SchedulerFacade;
import org.bukkit.Location;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DuelSessionContext {

    private final DuelSession session;
    private final AtomicBoolean cleanupStarted;

    private volatile PlayerSnapshot firstSnapshot;
    private volatile PlayerSnapshot secondSnapshot;
    private volatile Location firstTarget;
    private volatile Location secondTarget;
    private volatile String instanceWorldName;
    private volatile SchedulerFacade.TaskHandle countdownTask;
    private volatile SchedulerFacade.TaskHandle fightTimeoutTask;

    public DuelSessionContext(DuelSession session) {
        this.session = session;
        this.cleanupStarted = new AtomicBoolean(false);
    }

    public DuelSession session() {
        return session;
    }

    public PlayerSnapshot firstSnapshot() {
        return firstSnapshot;
    }

    public void setFirstSnapshot(PlayerSnapshot firstSnapshot) {
        this.firstSnapshot = firstSnapshot;
    }

    public PlayerSnapshot secondSnapshot() {
        return secondSnapshot;
    }

    public void setSecondSnapshot(PlayerSnapshot secondSnapshot) {
        this.secondSnapshot = secondSnapshot;
    }

    public Location firstTarget() {
        return firstTarget;
    }

    public void setFirstTarget(Location firstTarget) {
        this.firstTarget = firstTarget;
    }

    public Location secondTarget() {
        return secondTarget;
    }

    public void setSecondTarget(Location secondTarget) {
        this.secondTarget = secondTarget;
    }

    public String instanceWorldName() {
        return instanceWorldName;
    }

    public void setInstanceWorldName(String instanceWorldName) {
        this.instanceWorldName = instanceWorldName;
    }

    public boolean beginCleanup() {
        return cleanupStarted.compareAndSet(false, true);
    }

    public void setCountdownTask(SchedulerFacade.TaskHandle countdownTask) {
        if (this.countdownTask != null) {
            this.countdownTask.cancel();
        }
        this.countdownTask = countdownTask;
    }

    public void setFightTimeoutTask(SchedulerFacade.TaskHandle fightTimeoutTask) {
        if (this.fightTimeoutTask != null) {
            this.fightTimeoutTask.cancel();
        }
        this.fightTimeoutTask = fightTimeoutTask;
    }

    public void cancelTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (fightTimeoutTask != null) {
            fightTimeoutTask.cancel();
            fightTimeoutTask = null;
        }
    }
}
