package com.justrocketscience.gateway.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration properties for jrs-api-gateway.
 *
 * <p>Bound from the {@code jrs.jwt.*} namespace in {@code application.yml}.
 *
 * <p><b>Secret handling:</b> The {@code secret} field must NOT be set in
 * {@code application.yml}. Set it via the environment variable
 * {@code JRS_JWT_SECRET}, which AWS Secrets Manager injects at container
 * startup. Spring Boot maps {@code JRS_JWT_SECRET} →
 * {@code jrs.jwt.secret} automatically via relaxed binding.
 *
 * <p><b>Secret requirements (enforced by JJWT at startup):</b>
 * <ul>
 *   <li>Base64-encoded</li>
 *   <li>Decoded length ≥ 32 bytes (256 bits) for HS256</li>
 * </ul>
 * If the secret is too short, {@code JwtValidator} throws
 * {@code WeakKeyException} and the gateway refuses to start.
 * This is the correct behaviour — a weak secret is a deployment error.
 *
 * <p><b>Interview note:</b> Both auth-service and gateway read the same
 * Base64-encoded secret from environment variables. In AWS, a single
 * Secrets Manager secret is referenced by both ECS task definitions,
 * so there is one source of truth and no manual synchronisation.
 */
@Validated
@ConfigurationProperties(prefix = "jrs.jwt")
public class JwtProperties {

    /**
     * Base64-encoded HS256 shared secret.
     *
     * <p>Set via environment variable {@code JRS_JWT_SECRET}.
     * Never set this in application.yml.
     *
     * <p>Generate a suitable secret:
     * {@code openssl rand -base64 32}
     */
    @NotBlank(message = "JWT secret must be set via environment variable JRS_JWT_SECRET")
    private String secret;

    /**
     * Expected value of the {@code iss} claim in every token.
     * Must match the issuer configured in jrs-auth-service exactly.
     * Default: {@code jrs-auth-service}
     */
    @NotBlank
    private String issuer = "jrs-auth-service";

    /**
     * Expected value that must be present in the {@code aud} claim.
     * Must match the audience configured in jrs-auth-service exactly.
     * Default: {@code jrs-api-gateway}
     */
    @NotBlank
    private String audience = "jrs-api-gateway";

    /**
     * Allowed clock drift in seconds between the auth-service clock
     * and the gateway clock. Prevents spurious expiry failures caused
     * by minor time differences between hosts.
     * Default: 30 seconds.
     */
    @Min(0)
    private long clockSkewSeconds = 30;

    // -------------------------------------------------------------------------
    // Getters and setters — required by Spring Boot ConfigurationProperties
    // -------------------------------------------------------------------------

    public String getSecret()           { return secret; }
    public void   setSecret(String s)   { this.secret = s; }

    public String getIssuer()           { return issuer; }
    public void   setIssuer(String s)   { this.issuer = s; }

    public String getAudience()         { return audience; }
    public void   setAudience(String s) { this.audience = s; }

    public long getClockSkewSeconds()            { return clockSkewSeconds; }
    public void setClockSkewSeconds(long seconds) { this.clockSkewSeconds = seconds; }
}
