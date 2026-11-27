package dev.callseal.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.callseal.common.ApiException;
import dev.callseal.common.Hashing;
import dev.callseal.config.CallSealProperties;
import dev.callseal.intent.IntentDtos.CreateIntentRequest;
import dev.callseal.intent.IntentDtos.CreateIntentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class IntentService {

    private final IntentStore store;
    private final CodeGenerator codes;
    private final CallTokenService tokens;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final MeterRegistry metrics;
    private final Duration ttl;
    private final Clock clock;

    public IntentService(IntentStore store, CodeGenerator codes, CallTokenService tokens,
                         ApplicationEventPublisher events, ObjectMapper json, MeterRegistry metrics,
                         CallSealProperties props) {
        this.store = store;
        this.codes = codes;
        this.tokens = tokens;
        this.events = events;
        this.json = json;
        this.metrics = metrics;
        this.ttl = props.intentTtl();
        this.clock = Clock.systemUTC();
    }

    public CreateIntentResponse create(String tenantId, String idempotencyKey, CreateIntentRequest req) {
        Timer.Sample sample = Timer.start(metrics);
        try {
            return doCreate(tenantId, idempotencyKey, req);
        } finally {
            sample.stop(metrics.timer("callseal.intent.create.latency"));
        }
    }

    private CreateIntentResponse doCreate(String tenantId, String idemKey, CreateIntentRequest req) {
        if (idemKey == null || idemKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing Idempotency-Key",
                    "Send an Idempotency-Key header so retries don't announce the same call twice.");
        }
        String bodyHash = Hashing.sha256Hex(String.join("|",
                req.customerRef(), req.msisdn(), req.purpose().name(), req.agentId()));

        IntentStore.IdempotencyResult idem = store.reserveIdempotency(tenantId, idemKey, bodyHash, ttl);
        switch (idem.status()) {
            case REPLAY -> {
                return readResponse(idem.storedResponse());
            }
            case MISMATCH -> throw new ApiException(HttpStatus.CONFLICT, "Idempotency-Key reused",
                    "This key was already used with a different request body.");
            case IN_PROGRESS -> throw new ApiException(HttpStatus.CONFLICT, "Request in progress",
                    "A request with this Idempotency-Key is still being processed. Retry shortly.");
            case NEW -> { /* continue */ }
        }

        Instant now = clock.instant();
        String code = codes.generate();
        CallIntent intent = new CallIntent(
                UUID.randomUUID().toString(),
                tenantId,
                req.customerRef(),
                mask(req.msisdn()),
                req.purpose(),
                req.agentId(),
                codes.hash(code),
                IntentState.CREATED,
                now,
                now.plus(ttl));

        if (!store.saveNew(intent, ttl)) {
            store.releaseIdempotency(tenantId, idemKey);
            throw new ApiException(HttpStatus.CONFLICT, "Customer already has an open call",
                    "Only one announced call per customer at a time. Wait for it to finish or expire.");
        }

        CreateIntentResponse response = new CreateIntentResponse(
                intent.id(), tokens.sign(intent), code, intent.expiresAt());
        store.completeIdempotency(tenantId, idemKey, bodyHash, writeResponse(response), ttl);

        metrics.counter("callseal.intent.created", "purpose", req.purpose().name()).increment();
        // Audit (order 1) and push (order 2) react to this; push gets the plain code in memory only.
        events.publishEvent(new IntentEvent(IntentEvent.Type.CREATED, intent, code));
        return response;
    }

    public CallIntent getForTenant(String tenantId, String intentId) {
        CallIntent intent = store.find(intentId).orElseThrow(() -> new ApiException(HttpStatus.GONE,
                "Intent expired or unknown", "No open intent with this id."));
        if (!intent.tenantId().equals(tenantId)) {
            // Same answer as "unknown": don't reveal other tenants' ids.
            throw new ApiException(HttpStatus.GONE, "Intent expired or unknown", "No open intent with this id.");
        }
        return intent;
    }

    static String mask(String msisdn) {
        String digits = msisdn.replaceAll("[^0-9]", "");
        return "******" + digits.substring(Math.max(0, digits.length() - 4));
    }

    private String writeResponse(CreateIntentResponse r) {
        try {
            return json.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private CreateIntentResponse readResponse(String s) {
        try {
            return json.readValue(s, CreateIntentResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
