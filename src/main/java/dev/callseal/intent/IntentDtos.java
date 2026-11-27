package dev.callseal.intent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public final class IntentDtos {

    private IntentDtos() {
    }

    public record CreateIntentRequest(
            @NotBlank String customerRef,
            @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String msisdn,
            @NotNull Purpose purpose,
            @NotBlank String agentId) {
    }

    public record CreateIntentResponse(
            String intentId,
            String callToken,
            String spokenCode,
            Instant expiresAt) {
    }

    /** What the bank's voice agent sees. No code, no hash. */
    public record IntentStatusResponse(
            String intentId,
            String customerRef,
            Purpose purpose,
            IntentState state,
            Instant expiresAt) {

        static IntentStatusResponse of(CallIntent i) {
            return new IntentStatusResponse(i.id(), i.customerRef(), i.purpose(), i.state(), i.expiresAt());
        }
    }
}
