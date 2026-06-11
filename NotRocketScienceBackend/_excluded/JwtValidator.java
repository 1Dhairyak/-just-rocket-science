package com.justrocketscience.gateway.security;

import com.justrocketscience.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Date;
import java.util.List;

/**
 * Stateless JWT validator for the API Gateway.
 *
 * <p>Algorithm: HS256 (HMAC-SHA256) with a shared secret.
 * The same secret must be configured in jrs-auth-service and jrs-api-gateway.
 * Both services read it from the environment variable JWT_SECRET (injected via
 * AWS Secrets Manager at deployment time — never stored in application.yml).
 *
 * <p>Validation steps (each exits immediately on failure):
 * <ol>
 *   <li>Parse structure — 3-part JWT, Base64url-encoded</li>
 *   <li>HS256 signature against the shared SecretKey</li>
 *   <li>Expiry {@code exp} ± configurable clock skew</li>
 *   <li>Issuer {@code iss} matches {@code JwtProperties.issuer}</li>
 *   <li>Audience {@code aud} contains {@code JwtProperties.audience}</li>
 *   <li>Token type claim {@code type == "access"}</li>
 *   <li>JTI {@code jti} is non-blank</li>
 *   <li>Subject {@code sub} is non-blank</li>
 * </ol>
 *
 * <p>Thread safety: all fields are final and immutable after construction.
 * The singleton bean is safe to share across reactive threads.
 *
 * <p>Interview note: HS256 is chosen here because both services are deployed
 * together in a controlled AWS environment and share the secret via Secrets
 * Manager. RS256 would be appropriate if external parties needed to verify
 * tokens independently using a public key.
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String CLAIM_ROLES      = "roles";

    private final JwtParser  jwtParser;
    private final JwtProperties properties;
    private final Clock      clock;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Production constructor — uses system clock.
     * Spring calls this via @Component auto-detection.
     */
    public JwtValidator(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * Test constructor — accepts an injectable Clock so unit tests can pin
     * the current time and exercise expiry / clock-skew boundary behaviour
     * without manipulating system time.
     */
    JwtValidator(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock      = clock;

        // Build the shared HMAC-SHA256 key.
        // Keys.hmacShaKeyFor() enforces a minimum key length of 256 bits.
        // JJWT throws WeakKeyException at startup if the secret is too short —
        // the gateway will refuse to start, which is the correct behaviour.
        //
        // The secret must be Base64-encoded in configuration so that arbitrary
        // byte sequences can be stored safely in YAML / env vars.
        SecretKey secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(properties.getSecret())
        );

        // Build the parser once. JwtParser is stateless after construction
        // and safe to reuse across threads.
        //
        // clockSkewSeconds: allows a small drift between the auth-service clock
        // and the gateway clock. Recommended default is 30 seconds.
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .clockSkewSeconds(properties.getClockSkewSeconds())
                .build();

        log.info("JwtValidator initialised — algorithm=HS256, issuer={}, audience={}",
                properties.getIssuer(), properties.getAudience());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates a raw JWT string and returns a {@link ClaimsResult}.
     *
     * <p>Never throws — all exceptions are caught and converted to an invalid
     * {@code ClaimsResult} with a human-readable failure reason.
     *
     * @param token the raw Bearer token string (without the "Bearer " prefix)
     * @return {@code ClaimsResult.valid(...)} on success,
     *         {@code ClaimsResult.invalid(...)} on any failure
     */
    public ClaimsResult validate(String token) {
        try {
            // Steps 1 + 2: parse structure and verify HS256 signature.
            // JJWT throws MalformedJwtException, SignatureException, or
            // UnsupportedJwtException here before returning claims.
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();

            // Step 3: expiry is enforced by the parser via clockSkewSeconds.
            // If exp is in the past (beyond skew), ExpiredJwtException is thrown
            // before we reach this line.

            // Step 4: issuer check.
            String issuer = claims.getIssuer();
            if (!properties.getIssuer().equals(issuer)) {
                log.debug("JWT issuer mismatch — expected={}, got={}", properties.getIssuer(), issuer);
                return ClaimsResult.invalid("Invalid token issuer");
            }

            // Step 5: audience check.
            // JJWT stores aud as a Set<String> in JWT spec compliance mode.
            var audience = claims.getAudience(); // Set<String>
            if (audience == null || !audience.contains(properties.getAudience())) {
                log.debug("JWT audience mismatch — expected={}, got={}", properties.getAudience(), audience);
                return ClaimsResult.invalid("Invalid token audience");
            }

            // Step 6: token type must be "access".
            // Refresh tokens carry type="refresh" and must never be used as
            // Bearer credentials for protected endpoints.
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.debug("JWT token type invalid — expected=access, got={}", tokenType);
                return ClaimsResult.invalid("Invalid token type");
            }

            // Step 7: JTI must be present (used as the blacklist key in Redis).
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                log.debug("JWT missing jti claim");
                return ClaimsResult.invalid("Token ID (jti) is missing");
            }

            // Step 8: subject (user ID) must be present.
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                log.debug("JWT missing sub claim");
                return ClaimsResult.invalid("Token subject is missing");
            }

            List<String> roles = extractRoles(claims);
            return ClaimsResult.valid(subject, roles, jti);

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired — jti={}", safeJti(e));
            return ClaimsResult.invalid("Token has expired");

        } catch (SignatureException e) {
            // Signature failures are logged at WARN — they may indicate a
            // tampered token or a misconfigured secret.
            log.warn("JWT signature verification failed — possible tampered token or secret mismatch");
            return ClaimsResult.invalid("Token signature is invalid");

        } catch (MalformedJwtException e) {
            log.debug("JWT malformed — {}", e.getMessage());
            return ClaimsResult.invalid("Token is malformed");

        } catch (JwtException e) {
            log.debug("JWT validation failed — {}", e.getMessage());
            return ClaimsResult.invalid("Token validation failed");

        } catch (IllegalArgumentException e) {
            // Thrown by Keys.hmacShaKeyFor if secret is null/empty at runtime
            // (should be caught at startup, but guard defensively).
            log.error("JWT validation error — illegal argument: {}", e.getMessage());
            return ClaimsResult.invalid("Token validation error");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the roles claim, handling both List and String representations.
     * A missing roles claim returns an empty list (not a validation failure).
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object rolesRaw = claims.get(CLAIM_ROLES);
        if (rolesRaw instanceof List<?> list) {
            return (List<String>) list;
        }
        if (rolesRaw instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    /**
     * Safely extracts the JTI from an ExpiredJwtException for logging.
     * Returns "-" if the claims are unavailable.
     */
    private String safeJti(ExpiredJwtException e) {
        try {
            String jti = e.getClaims().getId();
            return jti != null ? jti : "-";
        } catch (Exception ignored) {
            return "-";
        }
    }

    // =========================================================================
    // ClaimsResult — inner value object
    // =========================================================================

    /**
     * Immutable result of a JWT validation attempt.
     *
     * <p>Use the factory methods {@link #valid} and {@link #invalid} —
     * there is no public constructor.
     */
    public static final class ClaimsResult {

        private final boolean      valid;
        private final String       userId;
        private final List<String> roles;
        private final String       jti;
        private final String       failureReason;

        private ClaimsResult(boolean valid, String userId,
                             List<String> roles, String jti,
                             String failureReason) {
            this.valid         = valid;
            this.userId        = userId;
            this.roles         = roles != null ? List.copyOf(roles) : List.of();
            this.jti           = jti;
            this.failureReason = failureReason;
        }

        /** Creates a successful result. */
        public static ClaimsResult valid(String userId, List<String> roles, String jti) {
            return new ClaimsResult(true, userId, roles, jti, null);
        }

        /** Creates a failed result with a reason suitable for RFC 7807 detail fields. */
        public static ClaimsResult invalid(String failureReason) {
            return new ClaimsResult(false, null, List.of(), null, failureReason);
        }

        public boolean      isValid()        { return valid; }
        public String       getUserId()      { return userId; }
        public List<String> getRoles()       { return roles; }
        public String       getJti()         { return jti; }
        public String       getFailureReason() { return failureReason; }

        @Override
        public String toString() {
            return valid
                    ? "ClaimsResult{valid=true, userId=" + userId + ", jti=" + jti + "}"
                    : "ClaimsResult{valid=false, reason=" + failureReason + "}";
        }
    }
}
