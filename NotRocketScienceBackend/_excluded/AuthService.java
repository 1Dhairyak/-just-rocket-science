package com.justrocketscience.auth.service;

import com.justrocketscience.auth.config.JwtConfig;
import com.justrocketscience.auth.dto.*;
import com.justrocketscience.auth.entity.RefreshToken;
import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.entity.UserRole;
import com.justrocketscience.auth.exception.*;
import com.justrocketscience.auth.mapper.UserMapper;
import com.justrocketscience.auth.repository.RefreshTokenRepository;
import com.justrocketscience.auth.repository.UserRepository;
import com.justrocketscience.auth.security.UserPrincipal;
import com.justrocketscience.auth.service.JwtService;
import com.justrocketscience.auth.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Core business layer for all authentication operations.
 *
 * <p>Transaction boundaries:
 * <ul>
 *   <li>{@code register}       — single write transaction (user insert)</li>
 *   <li>{@code login}          — single write transaction (refresh token insert)</li>
 *   <li>{@code refresh}        — single write transaction (revoke old + insert new)</li>
 *   <li>{@code logout}         — single write transaction (revoke token rows) + best-effort Redis write</li>
 *   <li>{@code changePassword} — single write transaction (password update + revoke all tokens)</li>
 *   <li>{@code getCurrentUser} — read-only transaction</li>
 * </ul>
 *
 * <p>No method spans multiple transactions. Redis writes (blacklist) are outside the DB
 * transaction intentionally — see {@link TokenBlacklistService} for the asymmetric failure strategy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final JwtConfig jwtConfig;

    // ─────────────────────────────────────────────────────────────────────────
    // register()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new user.
     *
     * <p>Uniqueness is checked with a single OR query before the insert to avoid
     * a partial write followed by a constraint violation. The check is <em>not</em>
     * transactionally safe against a race condition between two simultaneous registrations
     * of the same email/username — the DB unique constraint is the authoritative guard.
     *
     * <p>Registration does NOT return tokens. The client must perform an explicit login.
     * This keeps the registration response free of auth state and prevents token issuance
     * for accounts that fail post-registration steps (e.g. email verification in a future phase).
     *
     * <p>Audit log: registration attempt (pre-check), success with userId, or duplicate conflict.
     *
     * @param request validated registration payload
     * @return success message (no tokens)
     * @throws DuplicateUserException if email or username is already taken
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // AUDIT: registration attempt (log username only — email is PII)
        log.info("AUTH_REGISTER_ATTEMPT username={}", request.username());

        // Single round-trip uniqueness check — DB constraint is the final guard
        if (userRepository.existsByEmailOrUsername(request.email(), request.username())) {
            // Do not reveal which field conflicts — prevents user enumeration
            // AUDIT: duplicate detected
            log.warn("AUTH_REGISTER_CONFLICT username={}", request.username());
            throw new DuplicateUserException(
                "An account with that email or username already exists."
            );
        }

        User user = userMapper.toEntity(request);
        // Explicit encode — mapper NEVER touches password (unmappedTargetPolicy=ERROR enforces this)
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setIsActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);

        // AUDIT: registration success
        log.info("AUTH_REGISTER_SUCCESS userId={} username={}", saved.getId(), saved.getUsername());

        return new RegisterResponse("Registration successful. Please log in.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and issues a new access + refresh token pair.
     *
     * <p>{@link AuthenticationManager#authenticate} delegates to
     * {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider},
     * which calls {@code UserDetailsServiceImpl.loadUserByUsername(email)} and runs BCrypt.
     * This service never calls BCrypt directly for login — doing so would bypass the
     * timing-safe inactive-account handling in {@code UserDetailsServiceImpl}.
     *
     * <p>The refresh token is stored as a SHA-256 hash. The raw token is returned to the
     * client exactly once and is never stored.
     *
     * <p>If per-user session limits are enforced (future phase), check
     * {@code refreshTokenRepository.countActiveByUserId(userId)} before issuing.
     *
     * <p>Audit log: login attempt (email masked), success with userId, or failure type.
     *
     * @param request login credentials
     * @return token pair + expiry metadata
     * @throws BadCredentialsException if password does not match (propagated from AuthManager)
     * @throws DisabledException       if account is inactive (propagated from AuthManager)
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // AUDIT: login attempt — mask email to 3 chars + domain for log safety
        log.info("AUTH_LOGIN_ATTEMPT email={}", maskEmail(request.email()));

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException ex) {
            // AUDIT: login failure — bad credentials (do not log email in full)
            log.warn("AUTH_LOGIN_FAILED_BAD_CREDENTIALS email={}", maskEmail(request.email()));
            // Re-throw as-is; AuthController maps this to 401 via AuthExceptionHandler
            throw ex;
        } catch (DisabledException ex) {
            // AUDIT: login failure — account disabled (safe to log, not a security leak)
            log.warn("AUTH_LOGIN_FAILED_DISABLED email={}", maskEmail(request.email()));
            throw ex;
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID userId = principal.getUserId();

        String accessToken  = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        persistRefreshToken(userId, refreshToken, request.deviceInfo());

        // AUDIT: login success
        log.info("AUTH_LOGIN_SUCCESS userId={}", userId);

        return buildAuthResponse(accessToken, refreshToken);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // refresh()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rotates a refresh token, returning a new access + refresh token pair.
     *
     * <p>Rotation strategy: the old refresh token row is revoked atomically with the new row
     * insert in a single transaction. If anything fails, the old token remains valid and the
     * client can retry. There is no window where both tokens are invalid simultaneously.
     *
     * <p>Refresh token reuse detection: if the presented token is already revoked in the DB,
     * it signals potential token theft. All sessions for the user are immediately revoked as
     * a defensive response (the "refresh token family" pattern).
     *
     * <p>The access token is NOT validated here — it may be expired or about to expire,
     * which is the common reason the client is calling refresh. Only the refresh token
     * signature and expiry are checked.
     *
     * <p>Audit log: refresh attempt, rotation success, revoked-token reuse detection.
     *
     * @param request contains the raw refresh token string
     * @return new token pair
     * @throws InvalidTokenException if the token is malformed, expired, or wrong type
     * @throws TokenRevokedException if the token has been revoked (signals potential theft)
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String rawRefreshToken = request.refreshToken();

        // Step 1 — Validate JWT structure, signature, expiry, and type claim
        if (!jwtService.isRefreshTokenValid(rawRefreshToken)) {
            log.warn("AUTH_REFRESH_INVALID_TOKEN");
            throw new InvalidTokenException("Refresh token is invalid or expired.");
        }

        UUID userId = jwtService.extractUserId(rawRefreshToken);
        String tokenHash = hashToken(rawRefreshToken);

        // AUDIT: refresh attempt
        log.info("AUTH_REFRESH_ATTEMPT userId={}", userId);

        // Step 2 — Look up by hash (hottest query; hits idx_rt_token_hash)
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> {
                log.warn("AUTH_REFRESH_TOKEN_NOT_FOUND userId={}", userId);
                return new InvalidTokenException("Refresh token not found.");
            });

        // Step 3 — Reuse detection: presented token already revoked?
        if (storedToken.isRevoked()) {
            // Token theft signal — nuke all sessions for this user
            log.error("AUTH_REFRESH_TOKEN_REUSE_DETECTED userId={} — revoking all sessions", userId);
            refreshTokenRepository.revokeAllByUserId(userId);
            throw new TokenRevokedException(
                "Refresh token has already been used. All sessions have been revoked."
            );
        }

        // Step 4 — Expiry check (belt-and-suspenders; JWT validation above covers this)
        if (storedToken.isExpired()) {
            log.warn("AUTH_REFRESH_TOKEN_EXPIRED userId={}", userId);
            throw new InvalidTokenException("Refresh token has expired.");
        }

        // Step 5 — Load user for new token generation
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found for userId: " + userId));

        if (!user.getIsActive()) {
            log.warn("AUTH_REFRESH_ACCOUNT_DISABLED userId={}", userId);
            throw new AccountDisabledException("Account is disabled.");
        }

        UserPrincipal principal = UserPrincipal.from(user);

        // Step 6 — Generate new token pair
        String newAccessToken  = jwtService.generateAccessToken(principal);
        String newRefreshToken = jwtService.generateRefreshToken(principal);

        // Step 7 — Atomic rotation: revoke old, persist new (single transaction)
        refreshTokenRepository.revokeByTokenHash(tokenHash);
        persistRefreshToken(userId, newRefreshToken, storedToken.getDeviceInfo());

        // AUDIT: rotation success
        log.info("AUTH_REFRESH_SUCCESS userId={}", userId);

        return buildAuthResponse(newAccessToken, newRefreshToken);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // logout()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Logs out the current user from a single device (single-device logout).
     *
     * <p>Two-phase approach:
     * <ol>
     *   <li>Blacklist the access token JTI in Redis (best-effort, fail-open — see
     *       {@link TokenBlacklistService}). This blocks the token on all Gateway nodes
     *       within milliseconds.</li>
     *   <li>Revoke the matching refresh token row in the DB (by hash). This prevents
     *       token rotation from a logged-out session.</li>
     * </ol>
     *
     * <p>The access token JTI is extracted with {@code extractClaimsIgnoreExpiry()} so that
     * logout succeeds even if the token expired during the request transit window (race condition
     * between frontend calling logout and backend receiving it).
     *
     * <p>Idempotent: calling logout twice with the same token is safe — the blacklist write
     * is a no-op (Redis SETEX overwrites), and {@code revokeByTokenHash} has an
     * {@code AND revoked = false} guard.
     *
     * <p>Audit log: logout with userId + jti.
     *
     * @param rawAccessToken the Bearer token from the Authorization header (without "Bearer " prefix)
     * @param rawRefreshToken the refresh token from the request body (nullable for access-token-only logout)
     */
    @Transactional
    public void logout(String rawAccessToken, String rawRefreshToken) {
        UUID userId = null;
        String jti  = null;

        // Extract claims even if token has just expired (race condition safety)
        try {
            userId = jwtService.extractUserIdIgnoreExpiry(rawAccessToken);
            jti    = jwtService.extractJtiIgnoreExpiry(rawAccessToken);
        } catch (Exception ex) {
            // Malformed token — nothing to blacklist; silently succeed
            // (client-side logout should always appear successful)
            log.warn("AUTH_LOGOUT_MALFORMED_TOKEN — skipping blacklist");
            return;
        }

        // AUDIT: logout
        log.info("AUTH_LOGOUT userId={} jti={}", userId, jti);

        // Phase 1 — Blacklist access token JTI in Redis (outside transaction; fail-open)
        tokenBlacklistService.blacklistJti(jti, jwtService.getRemainingTtlSeconds(rawAccessToken));

        // Phase 2 — Revoke the matching refresh token row in DB (inside transaction)
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshTokenRepository.revokeByTokenHash(tokenHash);
            log.info("AUTH_LOGOUT_REFRESH_REVOKED userId={}", userId);
        }
        // If no refresh token was provided, only the access token is blacklisted.
        // The refresh token will naturally expire or be revoked on next use detection.
    }

    /**
     * Logs out the user from ALL devices.
     *
     * <p>Revokes all refresh token rows for the user and blacklists the current access token.
     * This is the response to a forced logout (e.g. password change, admin action).
     *
     * @param rawAccessToken current access token
     */
    @Transactional
    public void logoutAllDevices(String rawAccessToken) {
        UUID userId = null;
        String jti  = null;

        try {
            userId = jwtService.extractUserIdIgnoreExpiry(rawAccessToken);
            jti    = jwtService.extractJtiIgnoreExpiry(rawAccessToken);
        } catch (Exception ex) {
            log.warn("AUTH_LOGOUT_ALL_MALFORMED_TOKEN — skipping");
            return;
        }

        // AUDIT: logout all devices
        log.info("AUTH_LOGOUT_ALL_DEVICES userId={} jti={}", userId, jti);

        tokenBlacklistService.blacklistJti(jti, jwtService.getRemainingTtlSeconds(rawAccessToken));
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // changePassword()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Changes the authenticated user's password.
     *
     * <p>Security invariants:
     * <ul>
     *   <li>Requires the current password even with a valid JWT — defends against CSRF and
     *       stolen-but-unexpired tokens (same pattern as GitHub/Google).</li>
     *   <li>After a successful change, ALL active refresh tokens are revoked to force re-login
     *       on all devices. This is the correct response when a credential changes — an attacker
     *       who stole a refresh token should lose it immediately.</li>
     *   <li>The current access token is also blacklisted. The client must re-login.</li>
     *   <li>New password is validated to differ from the old one to prevent trivial re-use.</li>
     * </ul>
     *
     * <p>The update uses a targeted {@code UPDATE} query with an {@code isActive = true} guard —
     * it is not possible to set a password on a deactivated account even via a direct API call.
     *
     * <p>Audit log: password change attempt, success with userId.
     *
     * @param request      current + new password payload (validated by {@code @PasswordsNotEqual})
     * @param rawAccessToken the current access token (to blacklist after change)
     * @throws BadCredentialsException if current password does not match
     * @throws SamePasswordException   if new password is identical to current password
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request, String rawAccessToken) {
        UserPrincipal principal = requireAuthenticatedPrincipal();
        UUID userId = principal.getUserId();

        // AUDIT: password change attempt
        log.info("AUTH_CHANGE_PASSWORD_ATTEMPT userId={}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Authenticated user not found: " + userId));

        // Verify current password — BCrypt compare, not string equality
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // AUDIT: wrong current password
            log.warn("AUTH_CHANGE_PASSWORD_BAD_CURRENT_PASSWORD userId={}", userId);
            throw new BadCredentialsException("Current password is incorrect.");
        }

        // Guard: new password must differ from current (BCrypt re-hash comparison)
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new SamePasswordException("New password must differ from the current password.");
        }

        String newHash = passwordEncoder.encode(request.newPassword());

        // Targeted UPDATE — preserves all other fields, enforces isActive guard
        int updated = userRepository.updatePasswordHash(userId, newHash);
        if (updated == 0) {
            // Account was deactivated between the findById check and the UPDATE
            log.error("AUTH_CHANGE_PASSWORD_UPDATE_FAILED userId={} — account may be inactive", userId);
            throw new AccountDisabledException("Cannot change password for an inactive account.");
        }

        // Force re-login on all devices — revoke all refresh tokens first
        refreshTokenRepository.revokeAllByUserId(userId);

        // Blacklist the current access token — done outside the transaction (fail-open)
        try {
            String jti = jwtService.extractJtiIgnoreExpiry(rawAccessToken);
            tokenBlacklistService.blacklistJti(jti, jwtService.getRemainingTtlSeconds(rawAccessToken));
        } catch (Exception ex) {
            // Non-fatal — the refresh tokens are revoked; worst case the access token
            // remains usable for its remaining TTL (≤15 minutes).
            log.warn("AUTH_CHANGE_PASSWORD_JTI_BLACKLIST_FAILED userId={}", userId);
        }

        // AUDIT: password change success
        log.info("AUTH_CHANGE_PASSWORD_SUCCESS userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCurrentUser()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     *
     * <p>The {@link UserPrincipal} stored in {@link SecurityContextHolder} contains
     * {@code userId}, {@code email}, {@code role}, and {@code isActive} — no DB read is
     * needed for that subset. However, the full {@link UserProfileResponse} includes
     * {@code username} and {@code createdAt}, which are not in the principal to keep
     * JWT claims minimal. One DB read is performed.
     *
     * <p>This is a read-only transaction — no writes, no risk of dirty reads.
     *
     * @return profile response for the current user
     * @throws UserNotFoundException if the userId from the token no longer exists in the DB
     *                               (e.g. account permanently deleted — rare but possible)
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser() {
        UserPrincipal principal = requireAuthenticatedPrincipal();
        UUID userId = principal.getUserId();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("AUTH_GET_CURRENT_USER_NOT_FOUND userId={} — JWT references deleted account", userId);
                return new UserNotFoundException("User not found.");
            });

        return userMapper.toProfileResponse(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists a new refresh token row.
     *
     * <p>The raw token is hashed with SHA-256 before storage. The raw value is returned to
     * the client in the response and is never stored anywhere server-side.
     *
     * @param userId      the owning user
     * @param rawToken    the raw refresh token JWT string
     * @param deviceInfo  nullable User-Agent string for "active sessions" display
     */
    private void persistRefreshToken(UUID userId, String rawToken, String deviceInfo) {
        Instant now       = Instant.now();
        Instant expiresAt = now.plusMillis(jwtConfig.getRefreshTokenExpiry());

        RefreshToken entity = RefreshToken.builder()
            .userId(userId)
            .tokenHash(hashToken(rawToken))
            .expiresAt(expiresAt)
            .revoked(false)
            .deviceInfo(deviceInfo)
            .createdAt(now)
            .build();

        refreshTokenRepository.save(entity);
    }

    /**
     * Builds the {@link AuthResponse} returned to the client after login or refresh.
     */
    private AuthResponse buildAuthResponse(String accessToken, String refreshToken) {
        Instant expiresAt = Instant.now().plusMillis(jwtConfig.getAccessTokenExpiry());
        long expiresIn    = jwtConfig.getAccessTokenExpiry() / 1000; // seconds

        return new AuthResponse(
            accessToken,
            refreshToken,
            "Bearer",
            expiresIn,
            expiresAt
        );
    }

    /**
     * SHA-256 hash of a token, Base64-encoded.
     *
     * <p>The same algorithm must be used consistently for writes (login, refresh)
     * and reads (refresh lookup, logout lookup). The hash is deterministic for the
     * same input, so all comparisons are hash-to-hash.
     *
     * <p>SHA-256 is appropriate here because the raw token already contains a
     * high-entropy JWT signature; no salt is needed for this use case.
     *
     * @param rawToken the raw JWT string
     * @return Base64-encoded SHA-256 hash
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed by the JVM spec — this branch is unreachable
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * Extracts the {@link UserPrincipal} from the current {@link SecurityContextHolder}.
     *
     * <p>This will never return null in correctly guarded endpoints — Spring Security's
     * {@code authorizeHttpRequests} rejects unauthenticated requests before they reach
     * any service method. The explicit null check here is a defensive guard against
     * misconfigured routes.
     *
     * @return the authenticated principal
     * @throws AuthenticationRequiredException if the SecurityContext is empty (should never happen)
     */
    private UserPrincipal requireAuthenticatedPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AuthenticationRequiredException("No authenticated user in SecurityContext.");
        }
        return principal;
    }

    /**
     * Masks an email address for safe logging.
     *
     * <p>Example: {@code alice@example.com} → {@code ali***@example.com}
     * Preserves enough to correlate logs without leaking the full address as PII.
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local  = parts[0];
        String domain = parts[1];
        if (local.length() <= 3) return "***@" + domain;
        return local.substring(0, 3) + "***@" + domain;
    }
}
