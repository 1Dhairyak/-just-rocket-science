package com.justrocketscience.gateway.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate limiter configuration for jrs-api-gateway.
 *
 * <p>Bound from the {@code jrs.rate-limit.*} namespace in {@code application.yml}.
 *
 * <p>The gateway uses Spring Cloud Gateway's Redis-backed
 * {@code RequestRateLimiter} filter (token bucket algorithm). Two tiers
 * are defined:
 *
 * <ul>
 *   <li><b>Anonymous tier</b> — applied to public endpoints (register, login,
 *       refresh). Keyed by client IP via {@code IpKeyResolver}. Intentionally
 *       restrictive to prevent brute-force and credential-stuffing attacks.</li>
 *   <li><b>Authenticated tier</b> — applied to protected endpoints after JWT
 *       validation. Keyed by user ID via {@code UserIdKeyResolver}. More
 *       generous since the user's identity is verified.</li>
 * </ul>
 *
 * <p><b>Token bucket parameters explained simply:</b>
 * <ul>
 *   <li>{@code replenish-rate}: tokens added per second — sustained throughput.</li>
 *   <li>{@code burst-capacity}: maximum tokens available — allows short bursts
 *       above the sustained rate without being rate-limited.</li>
 * </ul>
 *
 * <p>Example: replenishRate=1, burstCapacity=5 → normally 1 req/s, but can
 * handle 5 rapid requests at once before throttling kicks in.
 */
@Validated
@ConfigurationProperties(prefix = "jrs.rate-limit")
public class RateLimitProperties {

    // -------------------------------------------------------------------------
    // Anonymous tier — public endpoints (login, register, refresh)
    // -------------------------------------------------------------------------

    /**
     * Tokens replenished per second for anonymous (unauthenticated) requests.
     * Default: 1 request/second per IP.
     */
    @Min(1)
    private int anonymousReplenishRate = 1;

    /**
     * Maximum token bucket capacity for anonymous requests.
     * Default: 5 — allows a short burst (e.g. browser retry logic) without
     * immediately rate-limiting a real user.
     */
    @Min(1)
    private int anonymousBurstCapacity = 5;

    // -------------------------------------------------------------------------
    // Authenticated tier — protected endpoints (after JWT validation)
    // -------------------------------------------------------------------------

    /**
     * Tokens replenished per second for authenticated requests.
     * Default: 10 requests/second per user ID.
     */
    @Min(1)
    private int authenticatedReplenishRate = 10;

    /**
     * Maximum token bucket capacity for authenticated requests.
     * Default: 20 — allows bursts from SPA frontends that fire multiple
     * parallel requests on page load.
     */
    @Min(1)
    private int authenticatedBurstCapacity = 20;

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public int getAnonymousReplenishRate()              { return anonymousReplenishRate; }
    public void setAnonymousReplenishRate(int v)        { this.anonymousReplenishRate = v; }

    public int getAnonymousBurstCapacity()              { return anonymousBurstCapacity; }
    public void setAnonymousBurstCapacity(int v)        { this.anonymousBurstCapacity = v; }

    public int getAuthenticatedReplenishRate()          { return authenticatedReplenishRate; }
    public void setAuthenticatedReplenishRate(int v)    { this.authenticatedReplenishRate = v; }

    public int getAuthenticatedBurstCapacity()          { return authenticatedBurstCapacity; }
    public void setAuthenticatedBurstCapacity(int v)    { this.authenticatedBurstCapacity = v; }
}
