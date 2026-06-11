package com.justrocketscience.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TokenBlacklistService using Mockito to mock StringRedisTemplate.
 *
 * No embedded Redis — tests run at JVM speed (~50ms).
 * Integration with real Redis is covered by AuthIntegrationTest (Testcontainers).
 *
 * Test strategy:
 *   - Happy path: write succeeds, read correctly reflects presence/absence
 *   - Redis failure: write fails → logout still succeeds (fail-open)
 *   - Redis failure: read fails → isBlacklisted returns true (fail-closed)
 *   - TTL edge cases: already-expired tokens, minimum TTL threshold
 *   - getExpire() return value semantics (-2, -1, > 0)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService")
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenBlacklistService blacklistService;

    private String testJti;

    @BeforeEach
    void setUp() {
        testJti = UUID.randomUUID().toString();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─── blacklistJti(String jti, Instant expiry) ─────────────────────────────

    @Nested
    @DisplayName("blacklistJti(jti, Instant)")
    class BlacklistJtiWithInstant {

        @Test
        @DisplayName("calls SETEX with correct key, value, and TTL")
        void callsSetexWithCorrectArguments() {
            Instant expiry = Instant.now().plusSeconds(900); // 15 min

            blacklistService.blacklistJti(testJti, expiry);

            verify(valueOps).set(
                    eq("token_blacklist:" + testJti),
                    eq("blacklisted"),
                    longThat(ttl -> ttl >= 895 && ttl <= 900), // allow 5s tolerance
                    eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("skips Redis write if token is effectively already expired (TTL < 1s)")
        void skipsWriteForAlreadyExpiredToken() {
            Instant expiredInstant = Instant.now().minusSeconds(5);

            blacklistService.blacklistJti(testJti, expiredInstant);

            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("logs and continues if Redis write throws (fail-open on logout)")
        void continuesIfRedisWriteFails() {
            Instant expiry = Instant.now().plusSeconds(900);
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(valueOps).set(anyString(), anyString(), anyLong(), any());

            // Should not throw — logout must succeed even if Redis is down
            blacklistService.blacklistJti(testJti, expiry);

            // Verify the write was attempted
            verify(valueOps).set(anyString(), anyString(), anyLong(), any());
        }
    }

    // ─── blacklistJti(String jti, long remainingTtlMillis) ───────────────────

    @Nested
    @DisplayName("blacklistJti(jti, long ttlMillis)")
    class BlacklistJtiWithMillis {

        @Test
        @DisplayName("converts milliseconds to seconds and calls SETEX")
        void convertsMillisToSeconds() {
            long ttlMillis = 900_000L; // 15 min

            blacklistService.blacklistJti(testJti, ttlMillis);

            verify(valueOps).set(
                    eq("token_blacklist:" + testJti),
                    eq("blacklisted"),
                    eq(900L),
                    eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("skips write if TTL in seconds is less than 1")
        void skipsWriteIfTtlTooSmall() {
            blacklistService.blacklistJti(testJti, 500L); // 0.5 seconds → 0s

            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("skips write for zero TTL")
        void skipsWriteForZeroTtl() {
            blacklistService.blacklistJti(testJti, 0L);
            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("skips write for negative TTL (already expired)")
        void skipsWriteForNegativeTtl() {
            blacklistService.blacklistJti(testJti, -5000L);
            verifyNoInteractions(valueOps);
        }
    }

    // ─── isBlacklisted() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("isBlacklisted()")
    class IsBlacklisted {

        @Test
        @DisplayName("returns true when Redis hasKey returns TRUE")
        void trueWhenKeyExists() {
            when(redisTemplate.hasKey("token_blacklist:" + testJti)).thenReturn(Boolean.TRUE);

            assertThat(blacklistService.isBlacklisted(testJti)).isTrue();
        }

        @Test
        @DisplayName("returns false when Redis hasKey returns FALSE")
        void falseWhenKeyAbsent() {
            when(redisTemplate.hasKey("token_blacklist:" + testJti)).thenReturn(Boolean.FALSE);

            assertThat(blacklistService.isBlacklisted(testJti)).isFalse();
        }

        @Test
        @DisplayName("returns true when Redis hasKey returns null (fail-closed)")
        void trueWhenRedisReturnsNull() {
            when(redisTemplate.hasKey("token_blacklist:" + testJti)).thenReturn(null);

            assertThat(blacklistService.isBlacklisted(testJti)).isTrue();
        }

        @Test
        @DisplayName("returns true when Redis throws an exception (fail-closed)")
        void trueWhenRedisThrows() {
            when(redisTemplate.hasKey(anyString()))
                    .thenThrow(new RuntimeException("Redis timeout"));

            // Must not throw — and must return true (fail-closed)
            assertThat(blacklistService.isBlacklisted(testJti)).isTrue();
        }

        @Test
        @DisplayName("checks the correctly namespaced key")
        void checksCorrectKey() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);

            blacklistService.isBlacklisted(testJti);

            verify(redisTemplate).hasKey("token_blacklist:" + testJti);
        }

        @Test
        @DisplayName("two different JTIs produce two different Redis keys")
        void differentJtisDifferentKeys() {
            String jti1 = UUID.randomUUID().toString();
            String jti2 = UUID.randomUUID().toString();

            when(redisTemplate.hasKey("token_blacklist:" + jti1)).thenReturn(Boolean.TRUE);
            when(redisTemplate.hasKey("token_blacklist:" + jti2)).thenReturn(Boolean.FALSE);

            assertThat(blacklistService.isBlacklisted(jti1)).isTrue();
            assertThat(blacklistService.isBlacklisted(jti2)).isFalse();
        }
    }

    // ─── remainingTtl() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("remainingTtl()")
    class RemainingTtl {

        @Test
        @DisplayName("returns Optional with TTL when key exists")
        void returnsOptionalWithTtl() {
            when(redisTemplate.getExpire("token_blacklist:" + testJti, TimeUnit.SECONDS))
                    .thenReturn(450L);

            Optional<Long> result = blacklistService.remainingTtl(testJti);

            assertThat(result).hasValue(450L);
        }

        @Test
        @DisplayName("returns empty Optional when Redis returns -2 (key does not exist)")
        void emptyWhenKeyAbsent() {
            when(redisTemplate.getExpire("token_blacklist:" + testJti, TimeUnit.SECONDS))
                    .thenReturn(-2L);

            assertThat(blacklistService.remainingTtl(testJti)).isEmpty();
        }

        @Test
        @DisplayName("returns empty Optional when Redis returns null")
        void emptyWhenRedisReturnsNull() {
            when(redisTemplate.getExpire("token_blacklist:" + testJti, TimeUnit.SECONDS))
                    .thenReturn(null);

            assertThat(blacklistService.remainingTtl(testJti)).isEmpty();
        }

        @Test
        @DisplayName("returns Optional.of(0) when Redis returns -1 (key exists, no TTL)")
        void returnsZeroWhenNoTtlSet() {
            when(redisTemplate.getExpire("token_blacklist:" + testJti, TimeUnit.SECONDS))
                    .thenReturn(-1L);

            // Defensive path — key exists but no TTL. Return 0 as a sentinel.
            assertThat(blacklistService.remainingTtl(testJti)).hasValue(0L);
        }

        @Test
        @DisplayName("returns empty Optional when Redis throws")
        void emptyWhenRedisThrows() {
            when(redisTemplate.getExpire(anyString(), any()))
                    .thenThrow(new RuntimeException("Redis down"));

            assertThat(blacklistService.remainingTtl(testJti)).isEmpty();
        }
    }

    // ─── Integration smoke: blacklist then check ──────────────────────────────

    @Nested
    @DisplayName("Full blacklist workflow")
    class BlacklistWorkflow {

        @Test
        @DisplayName("after blacklisting, isBlacklisted returns true")
        void blacklistThenCheck() {
            Instant expiry = Instant.now().plusSeconds(900);

            // Stub the SETEX call
            doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any());

            // Stub the hasKey call to return true (simulating Redis holding the key)
            when(redisTemplate.hasKey("token_blacklist:" + testJti)).thenReturn(Boolean.TRUE);

            blacklistService.blacklistJti(testJti, expiry);
            assertThat(blacklistService.isBlacklisted(testJti)).isTrue();
        }

        @Test
        @DisplayName("key not blacklisted returns false")
        void notBlacklistedReturnsFalse() {
            when(redisTemplate.hasKey("token_blacklist:" + testJti)).thenReturn(Boolean.FALSE);
            assertThat(blacklistService.isBlacklisted(testJti)).isFalse();
        }
    }
}
