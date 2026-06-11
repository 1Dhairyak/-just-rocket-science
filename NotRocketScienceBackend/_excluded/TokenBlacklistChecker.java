package com.justrocketscience.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive Redis-backed token blacklist checker.
 *
 * <h2>Purpose</h2>
 * <p>After a JWT passes signature and claims validation in {@link JwtValidator}, this component
 * performs a single Redis {@code EXISTS} lookup to confirm the token's JTI has not been
 * revoked. Revocation is initiated by auth-service (e.g. on logout or password change) by
 * writing the JTI to Redis with a TTL equal to the token's remaining lifetime.
 *
 * <h2>Redis key pattern</h2>
 * <pre>{@code blacklist:{jti}}</pre>
 * <p>Example: {@code blacklist:550e8400-e29b-41d4-a716-446655440000}
 *
 * <p>The key is written by auth-service and read here. Both sides must agree on the pattern —
 * the constant {@link #KEY_PREFIX} is the single source of truth on the gateway side.
 *
 * <h2>TTL awareness</h2>
 * <p>Auth-service sets the Redis key TTL to match the JWT's remaining {@code exp - now}
 * duration, ensuring:
 * <ul>
 *   <li>Blacklisted tokens are automatically evicted from Redis when they would have expired
 *       anyway — no manual cleanup required.</li>
 *   <li>The gateway never needs to consult Redis for tokens whose JTIs it has never seen —
 *       a missing key (Redis returns 0 from EXISTS) means the token is not blacklisted.</li>
 * </ul>
 * <p>This checker does not write to Redis and does not manage TTLs — it is read-only.
 *
 * <h2>Failure strategy — fail open</h2>
 * <p>If Redis is unavailable or returns an error, this component returns {@code false}
 * (not blacklisted) rather than failing the request. This is an intentional fail-open policy:
 * <ul>
 *   <li>Availability of the API is prioritised over perfect revocation enforcement during a
 *       Redis outage. A structurally valid JWT with a valid signature that has not expired is
 *       still a reasonably trustworthy credential.</li>
 *   <li>The alternative (fail-closed, return {@code true} = blacklisted) would drop all
 *       authenticated traffic during a Redis outage, including requests from users who have
 *       not logged out.</li>
 *   <li>The circuit breaker on the route (Resilience4j) provides a second layer of protection
 *       if the downstream service also becomes unreachable.</li>
 * </ul>
 * <p>A {@code WARN} log is emitted on every Redis failure so the operations team is alerted.
 * If a stricter fail-closed policy is required (e.g. for financial or compliance contexts),
 * replace the {@code onErrorReturn(false)} with {@code onErrorResume} that returns
 * {@code Mono.just(true)} and update the caller's 401 path accordingly.
 *
 * <h2>No blocking calls</h2>
 * <p>Uses {@link ReactiveRedisTemplate} throughout. No {@code .block()} calls exist anywhere
 * in this class — the returned {@code Mono<Boolean>} is composed into the reactive filter chain
 * in {@code JwtAuthenticationFilter}.
 */
@Component
public class TokenBlacklistChecker {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistChecker.class);

    /**
     * Redis key prefix for blacklisted JTIs.
     * Full key: {@code blacklist:{jti}}
     *
     * <p>Must match the prefix used by auth-service when writing revocation entries.
     * Do not change without updating auth-service simultaneously.
     */
    public static final String KEY_PREFIX = "blacklist:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public TokenBlacklistChecker(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Checks whether the given JWT ID (JTI) has been blacklisted.
     *
     * <p>Performs a single {@code EXISTS} call against Redis key {@code blacklist:{jti}}.
     *
     * <p>Caller contract:
     * <ul>
     *   <li>The JTI must be non-null and non-blank — validated by {@link JwtValidator} before
     *       this method is reached.</li>
     *   <li>The returned {@code Mono} never errors — all Redis failures are absorbed and
     *       return {@code false} with a warning log.</li>
     * </ul>
     *
     * @param jti the JWT ID claim ({@code jti}) from the validated token
     * @return {@code Mono<true>} if the token is blacklisted and must be rejected;
     *         {@code Mono<false>} if the token is not blacklisted or if Redis is unavailable
     */
    public Mono<Boolean> isBlacklisted(String jti) {
        String redisKey = KEY_PREFIX + jti;

        return redisTemplate
                .hasKey(redisKey)
                .defaultIfEmpty(false)
                .onErrorResume(ex -> {
                    log.warn("TokenBlacklistChecker: Redis lookup failed for jti={}. " +
                                    "Failing open (treating token as not blacklisted). error={}",
                            jti, ex.getMessage());
                    return Mono.just(false);
                })
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.debug("TokenBlacklistChecker: token blacklisted jti={}", jti);
                    }
                });
    }

    // =========================================================================
    // Package-visible helpers (for testing)
    // =========================================================================

    /**
     * Returns the Redis key that would be looked up for the given JTI.
     * Exposed for unit tests that need to verify the key pattern without calling Redis.
     */
    static String buildKey(String jti) {
        return KEY_PREFIX + jti;
    }
}
