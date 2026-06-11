package com.justrocketscience.auth.repository;

import com.justrocketscience.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link RefreshToken} aggregate.
 *
 * <p>Index-aware design — every query targets an indexed column
 * declared in V2__create_refresh_tokens.sql:
 * <ul>
 *   <li>idx_rt_token_hash        → findByTokenHash, existsByTokenHash</li>
 *   <li>idx_rt_user_id           → findAllByUserId, revokeAllByUserId</li>
 *   <li>idx_rt_expires_at        → deleteAllExpired (nightly cleanup)</li>
 *   <li>idx_rt_active_by_user    → findActiveByUserId (partial, non-revoked only)</li>
 * </ul>
 *
 * <p>Refresh tokens are write-once aggregates. The only mutation after insert
 * is flipping {@code revoked = TRUE}. Hard deletion is reserved for expired
 * token cleanup only — all other "deletions" are soft-revocations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // =========================================================================
    // LOOKUP — Token validation on every refresh request
    // =========================================================================

    /**
     * Primary lookup for every POST /auth/refresh call.
     * Hits idx_rt_token_hash — the hottest query in this table.
     *
     * <p>Returns Optional. Caller checks:
     * <ol>
     *   <li>present → token exists in DB</li>
     *   <li>{@link RefreshToken#isValid()} → not revoked AND not expired</li>
     * </ol>
     * Empty Optional = unknown token = 401 immediately.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Returns all refresh tokens (active + revoked) for a user.
     * Used by "active sessions" admin view.
     * Hits idx_rt_user_id.
     */
    List<RefreshToken> findAllByUserId(UUID userId);

    /**
     * Returns only non-revoked, non-expired tokens for a user.
     * Hits idx_rt_active_by_user (partial index WHERE revoked = FALSE).
     * Used to display "active sessions" to the end user.
     */
    @Query("""
            SELECT rt FROM RefreshToken rt
            WHERE rt.userId    = :userId
              AND rt.revoked   = false
              AND rt.expiresAt > :now
            ORDER BY rt.createdAt DESC
            """)
    List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId,
                                          @Param("now") Instant now);

    /**
     * Counts active sessions for a user — used for per-user session limits.
     * Example: block login if user already has 5 active devices.
     * Hits idx_rt_active_by_user.
     */
    @Query("""
            SELECT COUNT(rt) FROM RefreshToken rt
            WHERE rt.userId    = :userId
              AND rt.revoked   = false
              AND rt.expiresAt > :now
            """)
    long countActiveByUserId(@Param("userId") UUID userId,
                             @Param("now") Instant now);

    // =========================================================================
    // EXISTENCE CHECKS
    // =========================================================================

    /**
     * Fast path to detect token reuse without loading the entity.
     * If a tokenHash that was previously revoked is presented again,
     * it signals token theft — escalate to full session revocation.
     * Hits idx_rt_token_hash.
     */
    boolean existsByTokenHash(String tokenHash);

    /**
     * Checks whether a revoked token is being replayed.
     * A hit here means the refresh token was already used or
     * explicitly logged out — potential theft scenario.
     * Hits idx_rt_token_hash.
     */
    @Query("""
            SELECT COUNT(rt) > 0 FROM RefreshToken rt
            WHERE rt.tokenHash = :tokenHash
              AND rt.revoked   = true
            """)
    boolean isRevoked(@Param("tokenHash") String tokenHash);

    // =========================================================================
    // SINGLE-TOKEN REVOCATION — Normal logout flow
    // =========================================================================

    /**
     * Revokes a single token by its hash — used on POST /auth/logout.
     * Soft-revocation: sets revoked=TRUE + records when it was revoked.
     *
     * <p>Returns rows affected. Service asserts == 1.
     * If 0: token didn't exist (already expired/cleaned up) → still return 200,
     * don't leak information about token existence.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true
            WHERE rt.tokenHash = :tokenHash
              AND rt.revoked   = false
            """)
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);

    // =========================================================================
    // BULK REVOCATION — Logout-all-devices, account deactivation, theft response
    // =========================================================================

    /**
     * Revokes ALL tokens for a user — used for:
     * <ul>
     *   <li>POST /auth/logout?allDevices=true</li>
     *   <li>Password change (invalidate all existing sessions)</li>
     *   <li>Account deactivation</li>
     *   <li>Detected token theft — revoke everything immediately</li>
     * </ul>
     * Hits idx_rt_user_id. One UPDATE vs N round-trips.
     *
     * <p>Returns rows affected — useful for audit logging
     * ("revoked 3 sessions for user X after suspected theft").
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true
            WHERE rt.userId  = :userId
              AND rt.revoked = false
            """)
    int revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Revokes all tokens for a user EXCEPT the one just issued.
     * Used during token rotation: the new token is already persisted
     * before this runs, so we exclude it to avoid immediately revoking
     * the token we just handed back to the client.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true
            WHERE rt.userId  = :userId
              AND rt.id     != :excludeId
              AND rt.revoked = false
            """)
    int revokeAllByUserIdExcept(@Param("userId") UUID userId,
                                 @Param("excludeId") UUID excludeId);

    // =========================================================================
    // CLEANUP — Scheduled nightly job, keeps table lean
    // =========================================================================

    /**
     * Hard-deletes tokens that are past their expiry date.
     * These are truly dead rows — they can never be validated or replayed.
     * Safe to delete permanently unlike revoked-but-unexpired tokens
     * (which carry theft-detection value until they expire).
     *
     * <p>Hits idx_rt_expires_at — designed for this query.
     * Run nightly via {@code @Scheduled} — expected to process thousands of rows.
     *
     * <p>Returns count of deleted rows for monitoring/alerting.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM RefreshToken rt
            WHERE rt.expiresAt < :cutoff
            """)
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);

    /**
     * Hard-deletes tokens that are both revoked AND expired.
     * Companion to deleteAllExpiredBefore — cleans up revoked tokens
     * once they're past the window where theft detection matters.
     *
     * <p>Run weekly — keeps table from growing unbounded on high-traffic deployments.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM RefreshToken rt
            WHERE rt.revoked   = true
              AND rt.expiresAt < :cutoff
            """)
    int deleteRevokedExpiredBefore(@Param("cutoff") Instant cutoff);
}
