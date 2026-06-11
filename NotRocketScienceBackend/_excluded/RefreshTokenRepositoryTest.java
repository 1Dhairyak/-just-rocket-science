package com.justrocketscience.auth.repository;

import com.justrocketscience.auth.entity.RefreshToken;
import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jrs_auth_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestEntityManager em;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private UUID userId;
    private RefreshToken activeToken;
    private RefreshToken revokedToken;
    private RefreshToken expiredToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // RefreshToken stores userId as plain UUID — still needs a real user row
        // because of the FK constraint ON DELETE CASCADE
        User user = userRepository.save(User.builder()
                .username("token_user")
                .email("token@example.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.USER)
                .isActive(true)
                .build());
        userId = user.getId();

        activeToken = refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash("hash_active_token")
                .deviceInfo("Mozilla/5.0 Chrome/120")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        revokedToken = refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash("hash_revoked_token")
                .deviceInfo("Mozilla/5.0 Firefox/121")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build());

        expiredToken = refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash("hash_expired_token")
                .deviceInfo("Safari/17.0")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // already past
                .revoked(false)
                .build());

        em.flush();
        em.clear();
    }

    // =========================================================================
    @Nested
    @DisplayName("findByTokenHash")
    class FindByTokenHash {

        @Test
        @DisplayName("returns token for known hash")
        void returnsToken_forKnownHash() {
            Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("hash_active_token");

            assertThat(result).isPresent();
            assertThat(result.get().getRevoked()).isFalse();
        }

        @Test
        @DisplayName("returns empty for unknown hash")
        void returnsEmpty_forUnknownHash() {
            Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("hash_unknown");

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("isRevoked")
    class IsRevoked {

        @Test
        @DisplayName("returns true for revoked token")
        void returnsTrue_forRevokedToken() {
            assertThat(refreshTokenRepository.isRevoked("hash_revoked_token")).isTrue();
        }

        @Test
        @DisplayName("returns false for active token")
        void returnsFalse_forActiveToken() {
            assertThat(refreshTokenRepository.isRevoked("hash_active_token")).isFalse();
        }

        @Test
        @DisplayName("returns false for unknown hash — no information leakage")
        void returnsFalse_forUnknownHash() {
            assertThat(refreshTokenRepository.isRevoked("hash_unknown")).isFalse();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("findActiveByUserId")
    class FindActiveByUserId {

        @Test
        @DisplayName("returns only non-revoked, non-expired tokens")
        void returnsOnlyValidTokens() {
            List<RefreshToken> active = refreshTokenRepository.findActiveByUserId(userId, Instant.now());

            // activeToken is valid; revokedToken and expiredToken are excluded
            assertThat(active).hasSize(1);
            assertThat(active.get(0).getTokenHash()).isEqualTo("hash_active_token");
        }

        @Test
        @DisplayName("returns empty for user with no active sessions")
        void returnsEmpty_forUserWithNoActiveSessions() {
            // revoke the only active token first
            refreshTokenRepository.revokeAllByUserId(userId);
            em.flush(); em.clear();

            List<RefreshToken> active = refreshTokenRepository.findActiveByUserId(userId, Instant.now());

            assertThat(active).isEmpty();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("countActiveByUserId")
    class CountActiveByUserId {

        @Test
        @DisplayName("counts only valid active tokens")
        void countsOnlyValidTokens() {
            long count = refreshTokenRepository.countActiveByUserId(userId, Instant.now());

            // active=1, revoked=1 (excluded), expired=1 (excluded)
            assertThat(count).isEqualTo(1);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("revokeByTokenHash — single token logout")
    class RevokeByTokenHash {

        @Test
        @DisplayName("revokes active token — returns 1")
        void revokesActiveToken() {
            int affected = refreshTokenRepository.revokeByTokenHash("hash_active_token");

            assertThat(affected).isEqualTo(1);
            em.clear();
            assertThat(refreshTokenRepository.findByTokenHash("hash_active_token")
                    .orElseThrow().getRevoked()).isTrue();
        }

        @Test
        @DisplayName("returns 0 for already-revoked token — idempotent")
        void idempotent_forAlreadyRevokedToken() {
            int affected = refreshTokenRepository.revokeByTokenHash("hash_revoked_token");

            assertThat(affected).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 for unknown hash — does not throw")
        void returnsZero_forUnknownHash() {
            int affected = refreshTokenRepository.revokeByTokenHash("hash_unknown");

            assertThat(affected).isEqualTo(0);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("revokeAllByUserId — logout all devices")
    class RevokeAllByUserId {

        @Test
        @DisplayName("revokes all non-revoked tokens for the user")
        void revokesAll() {
            // user has 1 active + 1 expired (not revoked) = 2 revokeble
            int affected = refreshTokenRepository.revokeAllByUserId(userId);

            assertThat(affected).isEqualTo(2); // activeToken + expiredToken (both non-revoked)
            em.clear();
            assertThat(refreshTokenRepository.findActiveByUserId(userId, Instant.now())).isEmpty();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("revokeAllByUserIdExcept — token rotation")
    class RevokeAllByUserIdExcept {

        @Test
        @DisplayName("revokes all except the specified token — simulates rotation")
        void revokesAllExceptNewToken() {
            // Simulate: new token was just saved (activeToken is "new")
            // All others should be revoked
            int affected = refreshTokenRepository.revokeAllByUserIdExcept(
                    userId, activeToken.getId());

            // expiredToken is non-revoked and not excluded — should be revoked
            // revokedToken is already revoked (WHERE revoked=false skips it)
            assertThat(affected).isEqualTo(1);
            em.clear();

            // activeToken itself must still be active
            assertThat(refreshTokenRepository.findByTokenHash("hash_active_token")
                    .orElseThrow().getRevoked()).isFalse();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("deleteAllExpiredBefore — cleanup job")
    class DeleteAllExpiredBefore {

        @Test
        @DisplayName("hard-deletes expired tokens only")
        void deletesOnlyExpiredTokens() {
            long beforeCount = refreshTokenRepository.count();

            int deleted = refreshTokenRepository.deleteAllExpiredBefore(Instant.now());

            assertThat(deleted).isEqualTo(1); // only expiredToken
            assertThat(refreshTokenRepository.count()).isEqualTo(beforeCount - 1);

            // active and revoked tokens with future expiry are untouched
            assertThat(refreshTokenRepository.findByTokenHash("hash_active_token")).isPresent();
            assertThat(refreshTokenRepository.findByTokenHash("hash_revoked_token")).isPresent();
        }

        @Test
        @DisplayName("returns 0 when nothing to clean up")
        void returnsZero_whenNothingExpired() {
            // delete the expired one first
            refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
            em.flush(); em.clear();

            int deleted = refreshTokenRepository.deleteAllExpiredBefore(Instant.now());

            assertThat(deleted).isEqualTo(0);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("RefreshToken domain methods (isValid, isExpired)")
    class DomainMethods {

        @Test
        @DisplayName("isValid() true for active non-expired token")
        void isValid_true() {
            assertThat(activeToken.isValid()).isTrue();
        }

        @Test
        @DisplayName("isValid() false for revoked token")
        void isValid_false_whenRevoked() {
            assertThat(revokedToken.isValid()).isFalse();
        }

        @Test
        @DisplayName("isValid() false for expired token")
        void isValid_false_whenExpired() {
            assertThat(expiredToken.isValid()).isFalse();
        }

        @Test
        @DisplayName("revoke() sets revoked=true and token becomes invalid")
        void revoke_setsFlag() {
            activeToken.revoke();
            assertThat(activeToken.getRevoked()).isTrue();
            assertThat(activeToken.isValid()).isFalse();
        }
    }
}
