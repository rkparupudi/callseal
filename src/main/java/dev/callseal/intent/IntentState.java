package dev.callseal.intent;

/**
 * CREATED -> NOTIFIED -> VERIFIED | REPORTED.
 * An open intent that is never answered simply expires in Redis (ADR-002).
 */
public enum IntentState {
    CREATED,
    NOTIFIED,
    VERIFIED,
    REPORTED;

    public boolean isOpen() {
        return this == CREATED || this == NOTIFIED;
    }
}
