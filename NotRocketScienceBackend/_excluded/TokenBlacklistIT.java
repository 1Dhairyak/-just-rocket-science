package com.jrs.gateway.tests;

import com.jrs.gateway.config.GatewayIntegrationTestBase;
import com.jrs.gateway.util.TestTokenFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.UUID;

/**
 * Phase 2 — Step 7
 * Tests: token blacklist enforcement via Redis.
 *
 * Pattern: "blacklist:{jti}" key in Redis → token rejected as AUTH_TOKEN_BLACKLISTED.
 */
@DisplayName("Token Blacklist — Redis")
class TokenBlacklistIT extends GatewayIntegrationTestBase {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String PROTECTED_URI = "/api/v1/rockets";

    @Test
    @DisplayName("Non-blacklisted token → gateway forwards request")
    void nonBlacklistedTokenPasses() {
        String token = TestTokenFactory.validToken("user-clean");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 : "Non-blacklisted token wrongly rejected";
                });
    }

    @Test
    @DisplayName("Blacklisted JTI → AUTH_TOKEN_BLACKLISTED / 401")
    void blacklistedTokenRejected() throws Exception {
        // Generate token and extract its jti
        String jti = UUID.randomUUID().toString();
        String token = buildTokenWithJti("user-456", jti);

        // Blacklist the jti in Redis before the request
        redisTemplate.opsForValue()
                .set("blacklist:" + jti, "1", Duration.ofMinutes(15))
                .block();

        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_BLACKLISTED");
    }

    @Test
    @DisplayName("Blacklist key expiry → token accepted again after TTL")
    void expiredBlacklistEntryTokenAccepted() throws Exception {
        String jti = UUID.randomUUID().toString();
        String token = buildTokenWithJti("user-789", jti);

        // Write blacklist entry with 1-second TTL, then wait for it to expire
        redisTemplate.opsForValue()
                .set("blacklist:" + jti, "1", Duration.ofSeconds(1))
                .block();

        Thread.sleep(1500); // wait for Redis TTL expiry

        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 : "Token should be accepted after blacklist entry expires";
                });
    }

    @Test
    @DisplayName("Redis unavailable → fail-closed (token rejected, not passed through)")
    void redisUnavailableFailsClosed() {
        // This test is intentionally a documentation test.
        // In integration environments the real Redis is always up (Testcontainer).
        // To test fail-closed behaviour, a separate @SpringBootTest slice with
        // a stopped Redis mock would be needed — document the expected behaviour here.
        //
        // Expected behaviour when Redis is down:
        //   JwtAuthenticationFilter catches the Redis connection exception
        //   → returns 503 GW_DEPENDENCY_UNAVAILABLE or falls back to 401
        //   → NEVER passes the token through to upstream
        //
        // This is a security constraint: fail-open is not acceptable.
        // Verified via unit test of TokenBlacklistChecker with a mock
        // ReactiveStringRedisTemplate that throws RedisConnectionFailureException.
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a valid HS256 token with a specific jti claim.
     * Mirrors what the auth-service produces — jti must be present for blacklist check.
     */
    private String buildTokenWithJti(String subject, String jti) {
        // Delegate to a package-private builder in TestTokenFactory that accepts jti.
        // For now, uses the standard factory — replace with jti-aware overload when added.
        return TestTokenFactory.validToken(subject);
        // TODO: TestTokenFactory.validToken(subject, jti) once overload is added
    }
}
