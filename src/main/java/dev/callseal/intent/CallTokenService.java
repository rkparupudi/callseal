package dev.callseal.intent;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Signs the per-call token (ES256) with CallSeal's own key. The public key is published at
 * /.well-known/callseal-jwks.json so anyone holding a token can check it.
 *
 * The key is generated at startup (demo). Production: a KMS/HSM-held key with rotation by kid.
 */
@Component
public class CallTokenService {

    static final String ISSUER = "callseal";
    static final String AUDIENCE = "callseal";

    private final ECKey signingKey;

    public CallTokenService() throws JOSEException {
        this.signingKey = new ECKeyGenerator(Curve.P_256)
                .keyID("callseal-" + UUID.randomUUID())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES256)
                .generate();
    }

    public String sign(CallIntent intent) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(intent.customerRef())
                .audience(AUDIENCE)
                .jwtID(intent.id())
                .claim("tid", intent.tenantId())
                .claim("purpose", intent.purpose().name())
                .issueTime(Date.from(intent.createdAt()))
                .expirationTime(Date.from(intent.expiresAt()))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new ECDSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign call token", e);
        }
        return jwt.serialize();
    }

    public Map<String, Object> publicJwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    ECKey publicKeyForTests() {
        return signingKey.toPublicJWK();
    }
}
