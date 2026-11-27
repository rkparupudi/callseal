package dev.callseal.push;

import dev.callseal.config.CallSealProperties;
import dev.callseal.intent.CallIntent;
import dev.callseal.intent.IntentEvent;
import dev.callseal.intent.IntentState;
import dev.callseal.intent.IntentStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pushes call cards to open customer-app pages over Server-Sent Events (ADR-004).
 * Single-instance only: with several app instances, fan out through Redis pub/sub.
 *
 * Known demo limitation: a page that connects AFTER the call was announced misses the card,
 * because the plain code is never stored.
 */
@Component
public class SseHub {

    public record PushCard(String intentId, String bankName, String purpose, String spokenCode,
                           Instant expiresAt, IntentState state) {
    }

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger open = new AtomicInteger();
    private final IntentStore store;
    private final ApplicationEventPublisher events;
    private final CallSealProperties props;
    private final MeterRegistry metrics;

    public SseHub(IntentStore store, ApplicationEventPublisher events, CallSealProperties props, MeterRegistry metrics) {
        this.store = store;
        this.events = events;
        this.props = props;
        this.metrics = metrics;
        metrics.gauge("callseal.sse.connections", open);
    }

    public SseEmitter subscribe(String customerRef) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        emitters.computeIfAbsent(customerRef, k -> new CopyOnWriteArrayList<>()).add(emitter);
        open.incrementAndGet();
        Runnable remove = () -> {
            List<SseEmitter> list = emitters.get(customerRef);
            if (list != null && list.remove(emitter)) {
                open.decrementAndGet();
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    @EventListener
    @Order(2)
    public void on(IntentEvent event) {
        CallIntent intent = event.intent();
        PushCard card = new PushCard(intent.id(), props.displayNameFor(intent.tenantId()),
                intent.purpose().name(),
                event.type() == IntentEvent.Type.CREATED ? event.spokenCode() : null,
                intent.expiresAt(), intent.state());

        switch (event.type()) {
            case CREATED -> {
                int delivered = send(intent.customerRef(), "call", card);
                metrics.timer("callseal.push.delivery.latency")
                        .record(Duration.between(intent.createdAt(), Instant.now()));
                if (delivered > 0
                        && store.transition(intent.id(), IntentState.NOTIFIED) == IntentStore.TransitionResult.OK) {
                    events.publishEvent(IntentEvent.of(IntentEvent.Type.NOTIFIED, intent.withState(IntentState.NOTIFIED)));
                }
            }
            case VERIFIED, REPORTED -> send(intent.customerRef(), "status", card);
            case NOTIFIED -> { /* nothing to push */ }
        }
    }

    private int send(String customerRef, String eventName, PushCard card) {
        int delivered = 0;
        for (SseEmitter emitter : emitters.getOrDefault(customerRef, List.of())) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(card));
                delivered++;
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
        return delivered;
    }
}
