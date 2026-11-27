package dev.callseal.verdict;

import dev.callseal.intent.IntentState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEMO ONLY: customerRef comes from the request body. In a real bank app it comes from the
 * app's authenticated session, and this endpoint lives at /v1/call-intents/{id}/verdict.
 */
@RestController
public class VerdictController {

    public record VerdictRequest(@NotBlank String customerRef, @NotNull VerdictService.Verdict verdict) {
    }

    public record VerdictResponse(String intentId, IntentState state) {
    }

    private final VerdictService verdicts;

    public VerdictController(VerdictService verdicts) {
        this.verdicts = verdicts;
    }

    @PostMapping("/demo/call-intents/{id}/verdict")
    VerdictResponse verdict(@PathVariable String id, @Valid @RequestBody VerdictRequest request) {
        return new VerdictResponse(id, verdicts.record(id, request.customerRef(), request.verdict()));
    }
}
