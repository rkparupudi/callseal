package dev.callseal.push;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** DEMO ONLY: customerRef in the path stands in for the bank app's authenticated session. */
@RestController
public class PushController {

    private final SseHub hub;

    public PushController(SseHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/demo/customers/{customerRef}/pending-calls", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable String customerRef) {
        return hub.subscribe(customerRef);
    }
}
