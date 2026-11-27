package dev.callseal.intent;

import dev.callseal.intent.IntentDtos.CreateIntentRequest;
import dev.callseal.intent.IntentDtos.CreateIntentResponse;
import dev.callseal.intent.IntentDtos.IntentStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Bank-side API. The tenant is the OAuth client: the access token's subject. */
@RestController
public class IntentController {

    private final IntentService intents;
    private final CallTokenService tokens;

    public IntentController(IntentService intents, CallTokenService tokens) {
        this.intents = intents;
        this.tokens = tokens;
    }

    @PostMapping("/v1/call-intents")
    @ResponseStatus(HttpStatus.CREATED)
    CreateIntentResponse create(@AuthenticationPrincipal Jwt bank,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @Valid @RequestBody CreateIntentRequest request) {
        return intents.create(bank.getSubject(), idempotencyKey, request);
    }

    @GetMapping("/v1/call-intents/{id}")
    IntentStatusResponse get(@AuthenticationPrincipal Jwt bank, @PathVariable String id) {
        return IntentStatusResponse.of(intents.getForTenant(bank.getSubject(), id));
    }

    @GetMapping("/.well-known/callseal-jwks.json")
    Map<String, Object> jwks() {
        return tokens.publicJwks();
    }
}
