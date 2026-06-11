package com.justrocketscience.auth.config;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * JWT configuration bound from application.yml under the "application.jwt" prefix.
 *
 * Binding in application.yml:
 *
 *   application:
 *     jwt:
 *       secret: ${JWT_SECRET}          # base64-encoded 256-bit key, injected via env var
 *       access-token-expiry: 900000    # 15 minutes in ms
 *       refresh-token-expiry: 604800000 # 7 days in ms
 *
 * @Validated triggers Bean Validation on startup — if JWT_SECRET is missing or
 * either expiry is ≤ 0, the application will refuse to start with a clear error
 * rather than silently issuing broken tokens.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.jwt")
public class JwtConfig {

    /**
     * Base64-encoded HMAC-SHA256 secret key.
     * Must be at least 256 bits (32 bytes) → 44 base64 chars.
     * Injected as ${JWT_SECRET} — never hardcoded, never committed to source control.
     *
     * Generation command:
     *   openssl rand -base64 32
     */
    @NotBlank(message = "JWT secret must not be blank — set JWT_SECRET environment variable")
    private String secret;

    /**
     * Access token lifetime in milliseconds.
     * Default intent: 900_000 ms = 15 minutes.
     * Short-lived by design — stolen access tokens expire quickly.
     * @Min(60000) = 1 minute minimum to catch misconfiguration (0 or negative).
     */
    @Min(value = 60_000, message = "Access token expiry must be at least 60 000 ms (1 minute)")
    private long accessTokenExpiry;

    /**
     * Refresh token lifetime in milliseconds.
     * Default intent: 604_800_000 ms = 7 days.
     * Stored hashed in DB — rotation on every use, revocable server-side.
     */
    @Min(value = 300_000, message = "Refresh token expiry must be at least 300 000 ms (5 minutes)")
    private long refreshTokenExpiry;

    /**
     * Resolved SecretKey — built once on startup, reused for every sign/verify.
     * Not a config property; derived from {@code secret}.
     * Kept private so callers use getSecretKey() only — the raw secret string
     * is never handed out after initialisation.
     */
    private SecretKey secretKey;

    /**
     * Decodes the base64 secret string into a SecretKey exactly once.
     * Called by Spring after all properties are bound and validated.
     *
     * Keys.hmacShaKeyFor() enforces the JJWT requirement: the byte array must
     * be ≥ 256 bits for HS256. If the injected secret is too short, JJWT throws
     * WeakKeyException here — at startup, not at the first token issuance.
     */
    @PostConstruct
    public void buildSecretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Access token expiry in seconds (for the RFC 6749 "expires_in" field
     * in AuthResponse and as the "exp" claim offset).
     */
    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiry / 1000;
    }

    /**
     * Refresh token expiry in seconds.
     */
    public long getRefreshTokenExpirySeconds() {
        return refreshTokenExpiry / 1000;
    }
}
