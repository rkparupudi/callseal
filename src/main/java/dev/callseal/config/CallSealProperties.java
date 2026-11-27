package dev.callseal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under the "callseal" prefix in application.yml.
 *
 * @param intentTtl  how long an announced call stays open (also the call-token lifetime)
 * @param codePepper server-side secret mixed into the spoken-code hash (HMAC key)
 * @param demoBank   the single seeded tenant used by the local demo
 */
@ConfigurationProperties(prefix = "callseal")
public record CallSealProperties(Duration intentTtl, String codePepper, DemoBank demoBank) {

    public record DemoBank(String clientId, String clientSecret, String displayName) {
    }

    /** Display name shown on the customer's card. A real build would look this up per tenant. */
    public String displayNameFor(String tenantId) {
        return demoBank.clientId().equals(tenantId) ? demoBank.displayName() : tenantId;
    }
}
