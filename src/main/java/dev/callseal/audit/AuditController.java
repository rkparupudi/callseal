package dev.callseal.audit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    /** Walks the calling bank's whole chain and reports the first broken row, if any. */
    @GetMapping("/v1/audit/verify")
    AuditLog.VerifyResult verify(@AuthenticationPrincipal Jwt bank) {
        return audit.verify(bank.getSubject());
    }
}
