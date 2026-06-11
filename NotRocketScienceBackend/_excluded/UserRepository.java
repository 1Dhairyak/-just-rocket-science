package com.justrocketscience.auth.repository;

import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for the {@link User} aggregate.
 *
 * <p>Index-aware design — every derived query or JPQL below targets
 * an indexed column declared in V1__create_users.sql:
 * <ul>
 *   <li>idx_users_email    → findByEmail, existsByEmail</li>
 *   <li>idx_users_username → findByUsername, existsByUsername</li>
 *   <li>idx_users_active   → partial index on active users, used by findAllActive</li>
 * </ul>
 *
 * <p>Every method that mutates state is paired with {@code @Modifying}
 * and the calling service must run it inside a {@code @Transactional} boundary.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // =========================================================================
    // LOOKUP — Used by Spring Security and auth flows
    // =========================================================================

    /**
     * Primary login lookup.
     * Hits idx_users_email. Returns Optional — caller decides on 401 vs 404.
     */
    Optional<User> findByEmail(String email);

    /**
     * Profile and public-facing lookups by username.
     * Hits idx_users_username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Fetch by PK. Overridden here for documentation — JpaRepository
     * provides this but explicit declaration makes it searchable in the codebase.
     * Used after JWT validation: token carries sub=userId, service hydrates user.
     */
    @Override
    Optional<User> findById(UUID id);

    // =========================================================================
    // EXISTENCE CHECKS — Registration validation, uniqueness guards
    // =========================================================================

    /**
     * Checks email uniqueness before registration.
     * Spring Data generates: SELECT COUNT(*) > 0 WHERE email = ?
     * Hits idx_users_email. Prefer over findByEmail when you don't need the entity.
     */
    boolean existsByEmail(String email);

    /**
     * Checks username uniqueness before registration.
     * Hits idx_users_username.
     */
    boolean existsByUsername(String username);

    /**
     * Composite uniqueness check — registers both violations in one round-trip.
     * Used to return a single 409 that covers both fields simultaneously
     * rather than forcing the client to retry for the second failure.
     *
     * <p>JPQL deliberately uses OR so both columns are tested in one query.
     * Hits both idx_users_email and idx_users_username via index OR merge.
     */
    @Query("""
            SELECT COUNT(u) > 0
            FROM User u
            WHERE u.email = :email
               OR u.username = :username
            """)
    boolean existsByEmailOrUsername(@Param("email") String email,
                                    @Param("username") String username);

    // =========================================================================
    // ACTIVE-USER QUERIES — For admin dashboards, listings
    // =========================================================================

    /**
     * Returns the active user by email — the common case.
     * Combines the email index lookup with an isActive filter.
     * Avoids returning a deactivated account to the auth flow.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.email = :email
              AND u.isActive = true
            """)
    Optional<User> findActiveByEmail(@Param("email") String email);

    /**
     * Paginated list of all active users — used by admin endpoints.
     * Hits idx_users_active (partial index on is_active = TRUE).
     * Always paginated — never load all users into memory.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.isActive = true
            ORDER BY u.createdAt DESC
            """)
    Page<User> findAllActive(Pageable pageable);

    /**
     * Counts active users by role — admin stats endpoint.
     * Used for dashboard metrics: how many USER vs ADMIN accounts exist.
     */
    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE u.isActive = true
              AND u.role = :role
            """)
    long countActiveByRole(@Param("role") UserRole role);

    // =========================================================================
    // MUTATIONS — All require @Transactional at the service layer
    // =========================================================================

    /**
     * Soft-delete a user account. Sets is_active = FALSE — never hard-deletes.
     * Preserves audit trail and existing FK references from other services.
     *
     * <p>{@code @Modifying(clearAutomatically = true)} flushes and clears
     * the persistence context so subsequent finds in the same transaction
     * see the updated state rather than the stale cached entity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.isActive = false,
                u.updatedAt = :now
            WHERE u.id = :id
            """)
    int deactivateById(@Param("id") UUID id,
                       @Param("now") Instant now);

    /**
     * Update password hash after a successful changePassword flow.
     * Takes the BCrypt-encoded hash — never the raw password.
     *
     * <p>Returns int (rows affected). Service asserts == 1; anything else
     * triggers a rollback via {@code @Transactional(rollbackFor = Exception.class)}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.passwordHash = :passwordHash,
                u.updatedAt   = :now
            WHERE u.id = :id
              AND u.isActive = true
            """)
    int updatePasswordHash(@Param("id") UUID id,
                           @Param("passwordHash") String passwordHash,
                           @Param("now") Instant now);

    /**
     * Promote or demote a user's role. Admin-only operation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.role      = :role,
                u.updatedAt = :now
            WHERE u.id = :id
            """)
    int updateRole(@Param("id") UUID id,
                   @Param("role") UserRole role,
                   @Param("now") Instant now);

    // =========================================================================
    // ADMIN / AUDIT QUERIES
    // =========================================================================

    /**
     * Finds users registered within a time window — used by audit reports.
     * {@code Pageable} prevents unbounded result sets on large date ranges.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.createdAt BETWEEN :from AND :to
            ORDER BY u.createdAt DESC
            """)
    Page<User> findRegisteredBetween(@Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);

    /**
     * Bulk fetch by a list of IDs — used when another service sends a batch
     * of user IDs (e.g., for enrichment). Avoids N+1 SELECT in a loop.
     * Spring Data generates: WHERE id IN (:ids)
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.id IN :ids
              AND u.isActive = true
            """)
    List<User> findAllActiveByIds(@Param("ids") List<UUID> ids);
}
