package dev.callseal.intent;

import java.time.Instant;

/**
 * An announced call. Lives in Redis until it expires.
 * The spoken code is never stored: only its HMAC ({@code codeHash}).
 */
public record CallIntent(
        String id,
        String tenantId,
        String customerRef,
        String msisdnMasked,
        Purpose purpose,
        String agentId,
        String codeHash,
        IntentState state,
        Instant createdAt,
        Instant expiresAt) {

    public CallIntent withState(IntentState newState) {
        return new CallIntent(id, tenantId, customerRef, msisdnMasked, purpose, agentId, codeHash,
                newState, createdAt, expiresAt);
    }
}
