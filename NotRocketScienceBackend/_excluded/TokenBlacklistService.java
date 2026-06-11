package com.justrocketscience.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Manages the JWT access token blacklist using Redis SETEX.
 *
 * BLACKLIST STRATEGY
 * ──────────────────
 * Problem: JWTs are stateless — once issued, there is no server-side way to
 * invalidate them before expiry. Logout means nothing to a stateless token.
 *
 * Solution: On logout, extract the JTI (JWT ID claim), store it in Redis
 * with TTL = remaining token lifetime. Any request carrying that token is
 * rejected by JwtAuthenticationFilter BEFORE reaching the service.
 *
 * KEY DESIGN:
 *   Redis key: "token_blacklist:{jti}"
 *   Value:     "blacklisted"  (arbitrary; presence is what matters)
 *   TTL:       exact remaining milliseconds until the token would have expired
 *
 * This means:
 *   - The blacklist entry expires at the same time the token would have expired anyway
 *   - No stale keys accumulate — Redis auto-evicts via TTL
 *   - Memory usage is bounded: O(concurrent-sessions) at peak
 *   - A Redis outage degrades to "blacklist unavailable" — the filter's fail-open
 *     or fail-closed behaviour is a conscious choice (see isBlacklisted below)
 *
 * WHY NOT STORE ALL TOKENS (not just blacklisted ones)?
 *   Storing every issued token in Redis would make JWT effectively stateful,
 *   eliminating the scalability benefit. Blacklisting only revoked tokens
 *   keeps the Redis dataset small: only actively-logged-out sessions appear here.
 *
 * REDIS DATA STRUCTURE: String (not Set/List)
 *   Each blacklisted JTI gets its own key rather than a set because:
 *   - Individual TTL per entry (SET with EX) — impossible on set members
 *   - EXISTS check is O(1) — same as SISMEMBER
 *   - No risk of a single large key becoming a Redis hotspot
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    /** Redis key prefix. Namespaced to avoid collisions with other Redis users. */
    private static final String BLACKLIST_PREFIX = "token_blacklist:";

    /**
     * Minimum TTL to bother writing to Redis.
     * If a token expires in less than 1 second, it's effectively already invalid —
     * skip the Redis write to avoid a pointless round-trip.
     */
    private static final long MIN_TTL_SECONDS = 1L;

    private final StringRedisTemplate redisTemplate;

    // ─── Core Operations ──────────────────────────────────────────────────────

    /**
     * Adds a JTI to the blacklist with a TTL matching the token's remaining lifetime.
     *
     * @param jti            the JWT ID claim ("jti") from the token being invalidated
     * @param tokenExpiry    the token's expiry instant (from the "exp" claim)
     */
    public void blacklistJti(String jti, Instant tokenExpiry) {
        long ttlSeconds = computeTtlSeconds(tokenExpiry);

        if (ttlSeconds < MIN_TTL_SECONDS) {
            log.debug("Token JTI {} is effectively already expired (TTL={}s) — skipping blacklist write", jti, ttlSeconds);
            return;
        }

        String key = buildKey(jti);

        try {
            redisTemplate.opsForValue().set(key, "blacklisted", ttlSeconds, TimeUnit.SECONDS);
            log.debug("Blacklisted JTI {} with TTL={}s", jti, ttlSeconds);
        } catch (Exception e) {
            // Redis write failure on logout:
            // Options — fail closed (throw, prevent logout) or fail open (log, allow logout).
            // We fail OPEN: the user's intent is to log out. A Redis failure should not
            // trap them in a session. The token self-expires in ≤15 min.
            // Security trade-off is documented and acceptable for this TTL range.
            log.error("Redis write failed for JTI blacklist (jti={}). Token will self-expire at {}. Logout proceeds.", jti, tokenExpiry, e);
        }
    }

    /**
     * Overload that accepts remaining TTL in milliseconds directly.
     * Used when the caller has already computed remainingTtlMillis() from JwtService.
     *
     * @param jti               JWT ID claim
     * @param remainingTtlMillis remaining lifetime in milliseconds
     */
    public void blacklistJti(String jti, long remainingTtlMillis) {
        long ttlSeconds = remainingTtlMillis / 1000;

        if (ttlSeconds < MIN_TTL_SECONDS) {
            log.debug("Token JTI {} is effectively already expired — skipping blacklist write", jti);
            return;
        }

        String key = buildKey(jti);

        try {
            redisTemplate.opsForValue().set(key, "blacklisted", ttlSeconds, TimeUnit.SECONDS);
            log.debug("Blacklisted JTI {} with TTL={}s", jti, ttlSeconds);
        } catch (Exception e) {
            log.error("Redis write failed for JTI blacklist (jti={}). Logout proceeds despite Redis failure.", jti, e);
        }
    }

    /**
     * Checks whether a JTI is currently blacklisted.
     *
     * FAIL-CLOSED on Redis read failure:
     * Unlike blacklisting (where we fail open), a Redis READ failure during
     * authentication is treated as "cannot confirm this token is not blacklisted"
     * → return true (treat as blacklisted) → 401.
     *
     * Rationale: the asymmetry is intentional.
     *   - Logout (write) failing open: user is locked out for ≤15 min max. Acceptable.
     *   - Auth check (read) failing open: potentially logged-out tokens become valid
     *     for ≤15 min. With Redis HA (Redis Sentinel/Cluster), this path is rare
     *     and the fail-closed choice is the safer default.
     *
     * Operators should alert on Redis connectivity errors at the infrastructure level.
     *
     * @param jti  the JWT ID claim to check
     * @return true if the token should be rejected
     */
    public boolean isBlacklisted(String jti) {
        String key = buildKey(jti);

        try {
            Boolean exists = redisTemplate.hasKey(key);
            // hasKey() returns null if Redis returns an unexpected response type —
            // treat null as "key exists" (fail closed)
            return Boolean.TRUE.equals(exists) || exists == null;
        } catch (Exception e) {
            log.error("Redis read failed during blacklist check for JTI {}. Treating as blacklisted (fail-closed).", jti, e);
            return true; // fail closed — reject the request
        }
    }

    /**
     * Returns the remaining TTL of a blacklist entry in seconds.
     * Returns Optional.empty() if the key does not exist (token was never blacklisted
     * or the blacklist entry has already expired).
     *
     * Useful for:
     *   - Admin dashboards ("this user's session was invalidated X seconds ago")
     *   - Debugging ("why is this token being rejected?")
     *   - Integration tests asserting TTL was set correctly
     *
     * @param jti the JWT ID claim
     * @return remaining TTL in seconds, or empty if not present
     */
    public Optional<Long> remainingTtl(String jti) {
        String key = buildKey(jti);

        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

            // getExpire() return values:
            //  -2  → key does not exist
            //  -1  → key exists but has no TTL set (should never happen here — we always SETEX)
            //  > 0 → remaining TTL in seconds
            if (ttl == null || ttl == -2L) {
                return Optional.empty();
            }
            if (ttl == -1L) {
                // Key exists without TTL — defensive: log and return as present
                log.warn("Blacklist key {} has no TTL — this should not happen. Was SETEX used?", key);
                return Optional.of(0L);
            }
            return Optional.of(ttl);
        } catch (Exception e) {
            log.error("Redis TTL check failed for JTI {}", jti, e);
            return Optional.empty();
        }
    }

    /**
     * Returns true if a blacklist entry exists for the given JTI.
     * Convenience alias for !remainingTtl(jti).isEmpty() — prefer isBlacklisted()
     * in hot paths (single Redis EXISTS vs GET + TTL).
     */
    public boolean hasBlacklistEntry(String jti) {
        return remainingTtl(jti).isPresent();
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────

    /**
     * Builds the Redis key for a given JTI.
     * Namespaced to avoid key collisions with other Redis consumers in the same instance.
     */
    private String buildKey(String jti) {
        return BLACKLIST_PREFIX + jti;
    }

    /**
     * Computes the TTL in seconds between now and the token's expiry instant.
     * Clamped to 0 — never returns a negative value.
     */
    private long computeTtlSeconds(Instant tokenExpiry) {
        long ttlMillis = tokenExpiry.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0L, ttlMillis / 1000);
    }
}
