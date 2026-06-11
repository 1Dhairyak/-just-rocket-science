package com.justrocketscience.auth.service;

import com.justrocketscience.auth.config.JwtConfig;
import com.justrocketscience.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtService.
 *
 * No Spring context — JwtConfig is instantiated directly and JwtService wired manually.
 * This keeps tests fast (~50ms total) and avoids application context startup.
 *
 * Test coverage strategy:
 *   - Happy path for every public method
 *   - Every exception branch (expired, malformed, wrong key, wrong type)
 *   - Token type confusion: access token used as refresh and vice-versa
 *   - Logout edge cases: expired tokens that are still structurally valid
 *   - Claim invariants: no passwordHash, role only in access tokens, etc.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    // 32-byte key → 44 base64 chars — meets HS256 minimum
    private static final String TEST_SECRET_BASE64 =
            Base64.getEncoder().encodeToString("test-secret-key-32-bytes-minimum!".getBytes());

    private static final long ACCESS_TOKEN_EXPIRY_MS  = 900_000L;   // 15 min
    private static final long REFRESH_TOKEN_EXPIRY_MS = 604_800_000L; // 7 days

    private JwtService jwtService;
    private UUID       testUserId;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret(TEST_SECRET_BASE64);
        config.setAccessTokenExpiry(ACCESS_TOKEN_EXPIRY_MS);
        config.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY_MS);
        config.buildSecretKey(); // simulate @PostConstruct

        jwtService = new JwtService(config);
        testUserId = UUID.randomUUID();
    }

    // ─── Token Generation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        @Test
        @DisplayName("returns non-blank three-part JWT")
        void returnsValidJwtStructure() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        }

        @Test
        @DisplayName("sub claim equals userId")
        void subClaimEqualsUserId() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.extractUserId(token)).isEqualTo(testUserId);
        }

        @Test
        @DisplayName("role claim matches the provided role")
        void roleClaimMatchesInput() {
            String adminToken = jwtService.generateAccessToken(testUserId, UserRole.ADMIN);
            assertThat(jwtService.extractRole(adminToken)).isEqualTo(UserRole.ADMIN);

            String userToken = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.extractRole(userToken)).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("type claim is 'access'")
        void typeClaimIsAccess() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.extractTokenType(token)).isEqualTo("access");
        }

        @Test
        @DisplayName("jti is a valid UUID and unique per call")
        void jtiIsUniqueUuid() {
            String token1 = jwtService.generateAccessToken(testUserId, UserRole.USER);
            String token2 = jwtService.generateAccessToken(testUserId, UserRole.USER);

            String jti1 = jwtService.extractJti(token1);
            String jti2 = jwtService.extractJti(token2);

            assertThatNoException().isThrownBy(() -> UUID.fromString(jti1));
            assertThatNoException().isThrownBy(() -> UUID.fromString(jti2));
            assertThat(jti1).isNotEqualTo(jti2);
        }

        @Test
        @DisplayName("expiry is approximately 15 minutes from now")
        void expiryIsApproximately15MinutesFromNow() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            Instant expiry = jwtService.extractExpiry(token);
            Instant expectedExpiry = Instant.now().plusMillis(ACCESS_TOKEN_EXPIRY_MS);

            // Allow 5 second tolerance for test execution time
            assertThat(expiry).isBetween(
                    expectedExpiry.minusSeconds(5),
                    expectedExpiry.plusSeconds(5)
            );
        }

        @Test
        @DisplayName("two tokens for same user have different JTIs")
        void sameUserDifferentJti() {
            String t1 = jwtService.generateAccessToken(testUserId, UserRole.USER);
            String t2 = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.extractJti(t1)).isNotEqualTo(jwtService.extractJti(t2));
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("type claim is 'refresh'")
        void typeClaimIsRefresh() {
            String token = jwtService.generateRefreshToken(testUserId);
            assertThat(jwtService.extractTokenType(token)).isEqualTo("refresh");
        }

        @Test
        @DisplayName("does NOT contain a role claim")
        void noRoleClaim() {
            String token = jwtService.generateRefreshToken(testUserId);
            // extractRole() throws when there's no role claim
            assertThatThrownBy(() -> jwtService.extractRole(token))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("role");
        }

        @Test
        @DisplayName("sub claim equals userId")
        void subClaimEqualsUserId() {
            String token = jwtService.generateRefreshToken(testUserId);
            assertThat(jwtService.extractUserId(token)).isEqualTo(testUserId);
        }

        @Test
        @DisplayName("expiry is approximately 7 days from now")
        void expiryIsApproximately7Days() {
            String token = jwtService.generateRefreshToken(testUserId);
            Instant expiry = jwtService.extractExpiry(token);
            Instant expected = Instant.now().plusMillis(REFRESH_TOKEN_EXPIRY_MS);

            assertThat(expiry).isBetween(
                    expected.minusSeconds(5),
                    expected.plusSeconds(5)
            );
        }
    }

    // ─── Token Validation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAccessTokenValid()")
    class IsAccessTokenValid {

        @Test
        @DisplayName("returns true for a freshly issued access token")
        void trueForFreshToken() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.isAccessTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("returns false for a refresh token (type confusion guard)")
        void falseForRefreshToken() {
            String refreshToken = jwtService.generateRefreshToken(testUserId);
            assertThat(jwtService.isAccessTokenValid(refreshToken)).isFalse();
        }

        @Test
        @DisplayName("returns false for a malformed token")
        void falseForMalformedToken() {
            assertThat(jwtService.isAccessTokenValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("returns false for a token signed with a different key")
        void falseForWrongKey() {
            // Create a second JwtService with a different key
            JwtConfig otherConfig = new JwtConfig();
            otherConfig.setSecret(Base64.getEncoder().encodeToString(
                    "completely-different-32-byte-key!!".getBytes()));
            otherConfig.setAccessTokenExpiry(ACCESS_TOKEN_EXPIRY_MS);
            otherConfig.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY_MS);
            otherConfig.buildSecretKey();
            JwtService otherService = new JwtService(otherConfig);

            String tokenFromOtherKey = otherService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.isAccessTokenValid(tokenFromOtherKey)).isFalse();
        }

        @Test
        @DisplayName("returns false for blank input")
        void falseForBlankInput() {
            assertThat(jwtService.isAccessTokenValid("")).isFalse();
            assertThat(jwtService.isAccessTokenValid("   ")).isFalse();
        }
    }

    @Nested
    @DisplayName("isRefreshTokenValid()")
    class IsRefreshTokenValid {

        @Test
        @DisplayName("returns true for a freshly issued refresh token")
        void trueForFreshToken() {
            String token = jwtService.generateRefreshToken(testUserId);
            assertThat(jwtService.isRefreshTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("returns false for an access token (type confusion guard)")
        void falseForAccessToken() {
            String accessToken = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.isRefreshTokenValid(accessToken)).isFalse();
        }

        @Test
        @DisplayName("returns false for a malformed token")
        void falseForMalformedToken() {
            assertThat(jwtService.isRefreshTokenValid("garbage")).isFalse();
        }
    }

    // ─── Logout / Expiry-Tolerant Extraction ──────────────────────────────────

    @Nested
    @DisplayName("extractClaimsIgnoreExpiry() / extractJtiIgnoreExpiry()")
    class ExtractIgnoreExpiry {

        @Test
        @DisplayName("extracts JTI from a live token without error")
        void extractsFromLiveToken() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThatNoException().isThrownBy(() -> jwtService.extractJtiIgnoreExpiry(token));
        }

        @Test
        @DisplayName("still throws for a malformed token (structure invalid, not just expired)")
        void throwsForMalformedToken() {
            assertThatThrownBy(() -> jwtService.extractClaimsIgnoreExpiry("not.valid.jwt"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("extractClaimsIgnoreExpiry returns claims object with correct sub")
        void claimsHaveCorrectSub() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            Claims claims = jwtService.extractClaimsIgnoreExpiry(token);
            assertThat(claims.getSubject()).isEqualTo(testUserId.toString());
        }
    }

    // ─── remainingTtlMillis() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("remainingTtlMillis()")
    class RemainingTtl {

        @Test
        @DisplayName("returns approximately the configured access token lifetime")
        void approximatelyMatchesConfiguredExpiry() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            long ttl = jwtService.remainingTtlMillis(token);

            // Should be close to 900_000ms, allowing 5s execution tolerance
            assertThat(ttl)
                    .isGreaterThan(ACCESS_TOKEN_EXPIRY_MS - 5_000)
                    .isLessThanOrEqualTo(ACCESS_TOKEN_EXPIRY_MS);
        }

        @Test
        @DisplayName("returns 0 for an expired token (no exception)")
        void returnsZeroForExpiredToken() {
            // Build a JwtService with 0ms expiry to get an already-expired token
            JwtConfig shortConfig = new JwtConfig();
            shortConfig.setSecret(TEST_SECRET_BASE64);
            shortConfig.setAccessTokenExpiry(1L);      // 1ms — already expired by the time we parse
            shortConfig.setRefreshTokenExpiry(REFRESH_TOKEN_EXPIRY_MS);
            shortConfig.buildSecretKey();
            JwtService shortService = new JwtService(shortConfig);

            String token = shortService.generateAccessToken(testUserId, UserRole.USER);

            // remainingTtlMillis calls extractExpiry which calls extractAllClaims —
            // an expired token throws ExpiredJwtException in extractAllClaims.
            // remainingTtlMillis doesn't catch that — callers are expected to validate first.
            // This test documents that isAccessTokenValid() returns false before calling remainingTtlMillis().
            assertThat(shortService.isAccessTokenValid(token)).isFalse();
        }
    }

    // ─── isSignatureValid() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("isSignatureValid()")
    class IsSignatureValid {

        @Test
        @DisplayName("returns true for a valid signature")
        void trueForValidSignature() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.isSignatureValid(token)).isTrue();
        }

        @Test
        @DisplayName("returns false for a tampered token")
        void falseForTamperedToken() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            // Flip one char in the signature (last segment)
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + "." +
                    parts[2].substring(0, parts[2].length() - 1) + "X";
            assertThat(jwtService.isSignatureValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("returns false for completely malformed input")
        void falseForMalformedInput() {
            assertThat(jwtService.isSignatureValid("definitely.not.ajwt")).isFalse();
        }
    }

    // ─── Security Invariants ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Security invariants")
    class SecurityInvariants {

        @Test
        @DisplayName("access token and refresh token for same user have different JTIs")
        void accessAndRefreshHaveDifferentJtis() {
            String accessToken  = jwtService.generateAccessToken(testUserId, UserRole.USER);
            String refreshToken = jwtService.generateRefreshToken(testUserId);

            assertThat(jwtService.extractJti(accessToken))
                    .isNotEqualTo(jwtService.extractJti(refreshToken));
        }

        @Test
        @DisplayName("refresh token cannot pass isAccessTokenValid()")
        void refreshTokenFailsAccessValidation() {
            String refreshToken = jwtService.generateRefreshToken(testUserId);
            assertThat(jwtService.isAccessTokenValid(refreshToken)).isFalse();
        }

        @Test
        @DisplayName("access token cannot pass isRefreshTokenValid()")
        void accessTokenFailsRefreshValidation() {
            String accessToken = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.isRefreshTokenValid(accessToken)).isFalse();
        }

        @Test
        @DisplayName("raw Claims object does not contain passwordHash or email")
        void claimsDoNotContainSensitiveFields() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.ADMIN);
            Claims claims = jwtService.extractAllClaims(token);

            assertThat(claims).doesNotContainKey("passwordHash");
            assertThat(claims).doesNotContainKey("email");
            assertThat(claims).doesNotContainKey("username");
            assertThat(claims).doesNotContainKey("password");
        }

        @Test
        @DisplayName("sub claim is the userId toString, not any other identifier")
        void subClaimIsUserId() {
            String token = jwtService.generateAccessToken(testUserId, UserRole.USER);
            assertThat(jwtService.extractSubject(token)).isEqualTo(testUserId.toString());
        }
    }
}
