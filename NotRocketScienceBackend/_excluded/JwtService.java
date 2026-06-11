package com.justrocketscience.auth.service;

import com.justrocketscience.auth.config.JwtConfig;
import com.justrocketscience.auth.entity.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Core JWT service — all token generation, parsing, and validation logic lives here.
 *
 * TOKEN STRUCTURE DESIGN
 * ──────────────────────
 * Access Token (HS256, 15 min):
 * {
 *   "sub":  "550e8400-e29b-41d4-a716-446655440000",   ← user UUID
 *   "jti":  "7b3f1a2c-9d4e-4f5a-8b1c-2d3e4f5a6b7c",   ← unique token ID (for blacklisting)
 *   "role": "USER",                                    ← single role (not a list — simpler Gateway parsing)
 *   "type": "access",                                  ← explicit type guard
 *   "iat":  1700000000,
 *   "exp":  1700000900
 * }
 *
 * Refresh Token (HS256, 7 days):
 * {
 *   "sub":  "550e8400-e29b-41d4-a716-446655440000",
 *   "jti":  "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
 *   "type": "refresh",                                 ← type guard — prevents a refresh token being used as access
 *   "iat":  1700000000,
 *   "exp":  1700604800
 * }
 *
 * CLAIM DESIGN DECISIONS
 * ──────────────────────
 * "role" only in access token  → Refresh token has no role claim. If you use a refresh token
 *                                 where an access token is expected, the role extraction throws
 *                                 a MissingClaimException immediately.
 *
 * "type" claim in both         → Primary defence against token type confusion:
 *                                 using a refresh token to hit a protected API endpoint
 *                                 fails even if the signature and expiry are valid.
 *
 * "jti" = UUID in both         → Access: JTI is stored in Redis on logout for TTL = remaining life.
 *                                 Refresh: JTI is stored in DB (token_hash = SHA-256(raw token),
 *                                 but JTI lets us correlate without the raw token).
 *
 * No "email" or "username"     → Those are mutable. Embedding them means a username change
 *                                 silently invalidates all outstanding tokens. Only the immutable
 *                                 user UUID is embedded.
 *
 * ACCESS TOKEN vs REFRESH TOKEN STRATEGY
 * ───────────────────────────────────────
 * Access token:
 *   - Short-lived (15 min), stateless, validated at Gateway via signature + expiry + JTI blacklist
 *   - Never stored server-side (except JTI in Redis on logout, with TTL = remaining life)
 *   - Stolen tokens self-expire in ≤ 15 minutes; blacklist is the emergency brake
 *
 * Refresh token:
 *   - Long-lived (7 days), stateful — hashed copy stored in DB
 *   - One-time-use via rotation: each /refresh call revokes old token, issues new pair
 *   - Revoked token reuse detection: if revoked token is presented, revoke ALL user sessions
 *   - Never sent to any endpoint except POST /auth/refresh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS  = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtConfig jwtConfig;

    // ─── Token Generation ─────────────────────────────────────────────────────

    /**
     * Generates a signed access token for the given user.
     *
     * @param userId  the user's UUID (becomes the "sub" claim)
     * @param role    the user's current role (embedded in "role" claim)
     * @return signed compact JWT string
     */
    public String generateAccessToken(UUID userId, UserRole role) {
        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(jwtConfig.getAccessTokenExpiry());

        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())           // jti — unique per token issuance
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(jwtConfig.getSecretKey())         // HS256 inferred from SecretKey type
                .compact();
    }

    /**
     * Generates a signed refresh token for the given user.
     * No role claim — refresh tokens are only accepted at POST /auth/refresh.
     *
     * @param userId the user's UUID
     * @return signed compact JWT string
     */
    public String generateRefreshToken(UUID userId) {
        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(jwtConfig.getRefreshTokenExpiry());

        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(jwtConfig.getSecretKey())
                .compact();
    }

    // ─── Claim Extraction ─────────────────────────────────────────────────────

    /**
     * Extracts all claims from a token without performing type validation.
     * Used internally by extraction helpers.
     *
     * Throws {@link JwtException} (base class) on any parse failure:
     *   - SignatureException    → tampered or wrong key
     *   - ExpiredJwtException   → token past expiry
     *   - MalformedJwtException → not a valid JWT structure
     *
     * Callers of this method catch JwtException and map to application-level errors.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtConfig.getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the subject ("sub") claim — the user's UUID as a string.
     * Does NOT parse into UUID here; the caller decides whether UUID parsing is needed.
     */
    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extracts the user UUID from the "sub" claim.
     * Throws IllegalArgumentException if the sub is not a valid UUID.
     */
    public UUID extractUserId(String token) {
        String subject = extractSubject(token);
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Token subject is not a valid UUID: " + subject, e);
        }
    }

    /**
     * Extracts the JTI ("jti") claim — unique token identifier.
     * Used by TokenBlacklistService to blacklist on logout.
     */
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    /**
     * Extracts the role claim from an access token.
     * Throws MissingClaimException implicitly via Claims.get() returning null,
     * which we guard explicitly.
     */
    public UserRole extractRole(String token) {
        Claims claims = extractAllClaims(token);
        String roleStr = claims.get(CLAIM_ROLE, String.class);
        if (roleStr == null) {
            throw new JwtException("Token does not contain a role claim — may be a refresh token");
        }
        try {
            return UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Token contains unknown role: " + roleStr, e);
        }
    }

    /**
     * Extracts the token type ("access" or "refresh").
     */
    public String extractTokenType(String token) {
        String type = extractAllClaims(token).get(CLAIM_TYPE, String.class);
        if (type == null) {
            throw new JwtException("Token does not contain a type claim");
        }
        return type;
    }

    /**
     * Extracts the expiry as an Instant.
     */
    public Instant extractExpiry(String token) {
        return extractAllClaims(token).getExpiration().toInstant();
    }

    /**
     * Returns how many milliseconds remain until the token expires.
     * Returns 0 if the token is already expired (no exception — callers decide).
     */
    public long remainingTtlMillis(String token) {
        Instant expiry = extractExpiry(token);
        long remaining = expiry.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0L, remaining);
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    /**
     * Full access token validation:
     *   1. Signature verification (JJWT parser does this automatically)
     *   2. Expiry check (JJWT parser does this automatically)
     *   3. Type guard — must be "access", not "refresh"
     *
     * Does NOT check the JTI blacklist — that is the caller's responsibility
     * (JwtAuthenticationFilter calls TokenBlacklistService separately so the
     * Redis check is skipped on unauthenticated routes that don't reach the filter).
     *
     * @param token the raw JWT string
     * @return true if valid; false if any validation step fails (never throws)
     */
    public boolean isAccessTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get(CLAIM_TYPE, String.class);
            boolean typeValid = TYPE_ACCESS.equals(type);
            if (!typeValid) {
                log.warn("Token type check failed: expected 'access', got '{}'", type);
            }
            return typeValid;
        } catch (ExpiredJwtException e) {
            log.debug("Access token is expired: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.warn("Access token signature verification failed");
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Malformed access token");
            return false;
        } catch (JwtException e) {
            log.warn("Access token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Full refresh token validation:
     *   1. Signature verification
     *   2. Expiry check
     *   3. Type guard — must be "refresh", not "access"
     *
     * Does NOT check DB revocation — AuthService calls RefreshTokenRepository separately.
     *
     * @param token the raw JWT string
     * @return true if valid
     */
    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get(CLAIM_TYPE, String.class);
            boolean typeValid = TYPE_REFRESH.equals(type);
            if (!typeValid) {
                log.warn("Token type check failed: expected 'refresh', got '{}'", type);
            }
            return typeValid;
        } catch (ExpiredJwtException e) {
            log.debug("Refresh token is expired");
            return false;
        } catch (JwtException e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates only the signature and structure of a token, deliberately ignoring expiry.
     *
     * Used by the logout endpoint: an expired token can still be legitimately presented
     * for logout (the user's UI may be slow to redirect). We want to extract the JTI
     * for blacklisting even if the token has just expired.
     *
     * Using a Clock that always reports "just now" would require mocking in tests;
     * catching ExpiredJwtException and re-parsing the payload is cleaner.
     */
    public boolean isSignatureValid(String token) {
        try {
            // Normal parse — if it throws anything other than ExpiredJwtException, signature/structure is bad
            extractAllClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Expired but structurally valid — signature was good
            return true;
        } catch (SignatureException | MalformedJwtException e) {
            return false;
        } catch (JwtException e) {
            log.warn("Token signature check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts claims from an expired token without throwing ExpiredJwtException.
     * Used by the logout flow to get the JTI from a just-expired token.
     */
    public Claims extractClaimsIgnoreExpiry(String token) {
        try {
            return extractAllClaims(token);
        } catch (ExpiredJwtException e) {
            // The Claims are still available on the exception — JJWT provides them
            return e.getClaims();
        }
    }

    /**
     * Convenience: extract JTI from a token that may be expired.
     * Delegates to extractClaimsIgnoreExpiry — safe for logout path.
     */
    public String extractJtiIgnoreExpiry(String token) {
        return extractClaimsIgnoreExpiry(token).getId();
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Not exposed publicly — use the typed extraction methods.
     * Kept private to prevent callers from depending on raw Map access
     * and accidentally bypassing type validation.
     */
    private <T> T extractClaim(String token, java.util.function.Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }
}
