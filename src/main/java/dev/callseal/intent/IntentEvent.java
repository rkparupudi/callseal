package dev.callseal.intent;

/**
 * Published inside the JVM whenever an intent changes. Modules (audit, push) listen; they never
 * call each other directly (ADR-001).
 *
 * @param spokenCode plain-text code, present ONLY on CREATED so the push module can show it.
 *                   Never written to Redis, Postgres or logs.
 */
public record IntentEvent(Type type, CallIntent intent, String spokenCode) {

    public enum Type { CREATED, NOTIFIED, VERIFIED, REPORTED }

    public static IntentEvent of(Type type, CallIntent intent) {
        return new IntentEvent(type, intent, null);
    }

    @Override
    public String toString() {
        // Keeps the code out of any accidental log line.
        return "IntentEvent[" + type + ", " + intent.id() + "]";
    }
}
