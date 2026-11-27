package dev.callseal.intent;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CallTokenServiceTest {

    @Test
    void signsVerifiableTokenWithExpectedClaims() throws Exception {
        CallTokenService service = new CallTokenService();
        Instant now = Instant.parse("2026-12-01T10:00:00Z");
        CallIntent intent = new CallIntent("intent-1", "demo-bank", "cust-001", "******0001",
                Purpose.EMI_REMINDER, "agent-1", "hash", IntentState.CREATED, now, now.plusSeconds(300));

        SignedJWT jwt = SignedJWT.parse(service.sign(intent));

        assertThat(jwt.verify(new ECDSAVerifier(service.publicKeyForTests()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getJWTID()).isEqualTo("intent-1");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("cust-001");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("callseal");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("tid")).isEqualTo("demo-bank");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(300));
        // The spoken code must never appear in the token.
        assertThat(jwt.getJWTClaimsSet().getClaims()).doesNotContainKeys("code", "spokenCode", "codeHash");
    }

    @Test
    void masksMsisdnToLastFourDigits() {
        assertThat(IntentService.mask("+91 98000 00001")).isEqualTo("******0001");
    }
}
