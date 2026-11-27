package dev.callseal.intent;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis storage for open intents (ADR-002).
 *
 * Keys:
 *   intent:{id}                 hash, TTL = intent lifetime
 *   active:{customerRef}        id of the customer's one open intent, same TTL
 *   idem:{tenant}:{key}         idempotency record: "PENDING|bodyHash" or "DONE|bodyHash|responseJson"
 */
@Component
public class IntentStore {

    /** Atomic compare-and-set: only an open intent may move, and only once to a terminal state. */
    private static final DefaultRedisScript<Long> TRANSITION = new DefaultRedisScript<>("""
            local state = redis.call('HGET', KEYS[1], 'state')
            if not state then return -1 end
            if state ~= 'CREATED' and state ~= 'NOTIFIED' then return 0 end
            if ARGV[1] == 'NOTIFIED' and state == 'NOTIFIED' then return 0 end
            redis.call('HSET', KEYS[1], 'state', ARGV[1])
            return 1
            """, Long.class);

    public enum TransitionResult { OK, ALREADY_FINAL, NOT_FOUND }

    public enum IdempotencyStatus { NEW, REPLAY, IN_PROGRESS, MISMATCH }

    public record IdempotencyResult(IdempotencyStatus status, String storedResponse) {
    }

    private final StringRedisTemplate redis;

    public IntentStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Saves a new intent. Returns false if the customer already has an open intent
     * (one active call per customer keeps the code unambiguous).
     */
    public boolean saveNew(CallIntent i, Duration ttl) {
        Boolean claimed = redis.opsForValue().setIfAbsent(activeKey(i.customerRef()), i.id(), ttl);
        if (!Boolean.TRUE.equals(claimed)) {
            return false;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put("id", i.id());
        fields.put("tenantId", i.tenantId());
        fields.put("customerRef", i.customerRef());
        fields.put("msisdnMasked", i.msisdnMasked());
        fields.put("purpose", i.purpose().name());
        fields.put("agentId", i.agentId());
        fields.put("codeHash", i.codeHash());
        fields.put("state", i.state().name());
        fields.put("createdAt", i.createdAt().toString());
        fields.put("expiresAt", i.expiresAt().toString());
        redis.opsForHash().putAll(intentKey(i.id()), fields);
        redis.expire(intentKey(i.id()), ttl);
        return true;
    }

    public Optional<CallIntent> find(String id) {
        Map<Object, Object> h = redis.opsForHash().entries(intentKey(id));
        if (h.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CallIntent(
                (String) h.get("id"),
                (String) h.get("tenantId"),
                (String) h.get("customerRef"),
                (String) h.get("msisdnMasked"),
                Purpose.valueOf((String) h.get("purpose")),
                (String) h.get("agentId"),
                (String) h.get("codeHash"),
                IntentState.valueOf((String) h.get("state")),
                Instant.parse((String) h.get("createdAt")),
                Instant.parse((String) h.get("expiresAt"))));
    }

    public TransitionResult transition(String id, IntentState target) {
        Long r = redis.execute(TRANSITION, List.of(intentKey(id)), target.name());
        if (r == null || r == -1) {
            return TransitionResult.NOT_FOUND;
        }
        return r == 1 ? TransitionResult.OK : TransitionResult.ALREADY_FINAL;
    }

    /** Frees the customer for a new call once this one is final. */
    public void releaseActive(String customerRef, String intentId) {
        String current = redis.opsForValue().get(activeKey(customerRef));
        if (intentId.equals(current)) {
            redis.delete(activeKey(customerRef));
        }
    }

    public IdempotencyResult reserveIdempotency(String tenantId, String key, String bodyHash, Duration ttl) {
        String redisKey = idemKey(tenantId, key);
        Boolean fresh = redis.opsForValue().setIfAbsent(redisKey, "PENDING|" + bodyHash, ttl);
        if (Boolean.TRUE.equals(fresh)) {
            return new IdempotencyResult(IdempotencyStatus.NEW, null);
        }
        String existing = redis.opsForValue().get(redisKey);
        if (existing == null) {
            // Expired between the two calls; treat as new by retrying once.
            return reserveIdempotency(tenantId, key, bodyHash, ttl);
        }
        String[] parts = existing.split("\\|", 3);
        if (!parts[1].equals(bodyHash)) {
            return new IdempotencyResult(IdempotencyStatus.MISMATCH, null);
        }
        if ("PENDING".equals(parts[0])) {
            return new IdempotencyResult(IdempotencyStatus.IN_PROGRESS, null);
        }
        return new IdempotencyResult(IdempotencyStatus.REPLAY, parts[2]);
    }

    /**
     * Stores the finished response for replays. Note: this includes the spoken code in Redis
     * for the intent's lifetime, a deliberate trade-off so a retried request gets the same code.
     */
    public void completeIdempotency(String tenantId, String key, String bodyHash, String responseJson, Duration ttl) {
        redis.opsForValue().set(idemKey(tenantId, key), "DONE|" + bodyHash + "|" + responseJson, ttl);
    }

    public void releaseIdempotency(String tenantId, String key) {
        redis.delete(idemKey(tenantId, key));
    }

    private static String intentKey(String id) {
        return "intent:" + id;
    }

    private static String activeKey(String customerRef) {
        return "active:" + customerRef;
    }

    private static String idemKey(String tenantId, String key) {
        return "idem:" + tenantId + ":" + key;
    }
}
