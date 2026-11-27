package dev.callseal.verdict;

import dev.callseal.common.ApiException;
import dev.callseal.intent.CallIntent;
import dev.callseal.intent.IntentEvent;
import dev.callseal.intent.IntentState;
import dev.callseal.intent.IntentStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** The customer's answer: this call is genuine, or report it. */
@Service
public class VerdictService {

    public enum Verdict { GENUINE, REPORTED }

    private final IntentStore store;
    private final ApplicationEventPublisher events;
    private final MeterRegistry metrics;

    public VerdictService(IntentStore store, ApplicationEventPublisher events, MeterRegistry metrics) {
        this.store = store;
        this.events = events;
        this.metrics = metrics;
    }

    public IntentState record(String intentId, String customerRef, Verdict verdict) {
        CallIntent intent = store.find(intentId).orElseThrow(() -> new ApiException(HttpStatus.GONE,
                "Call expired", "This call is no longer open."));
        if (!intent.customerRef().equals(customerRef)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not your call", "No open call for this customer.");
        }

        IntentState target = verdict == Verdict.GENUINE ? IntentState.VERIFIED : IntentState.REPORTED;
        IntentStore.TransitionResult result = store.transition(intentId, target);
        switch (result) {
            case NOT_FOUND -> throw new ApiException(HttpStatus.GONE, "Call expired", "This call is no longer open.");
            case ALREADY_FINAL -> throw new ApiException(HttpStatus.CONFLICT, "Already answered",
                    "A verdict was already recorded for this call.");
            case OK -> { /* continue */ }
        }

        store.releaseActive(customerRef, intentId);
        metrics.counter("callseal.intent.outcome", "outcome", target.name()).increment();
        IntentEvent.Type type = target == IntentState.VERIFIED ? IntentEvent.Type.VERIFIED : IntentEvent.Type.REPORTED;
        events.publishEvent(IntentEvent.of(type, intent.withState(target)));
        return target;
    }
}
