package me.cookie.duel.duel.session;

public enum DuelSessionState {
    IDLE,
    QUEUED,
    MATCH_FOUND,
    CONFIRMING,
    PROVISIONING,
    TELEPORTING,
    FIGHTING,
    ENDING,
    CLEANUP,
    CANCELLED
}
