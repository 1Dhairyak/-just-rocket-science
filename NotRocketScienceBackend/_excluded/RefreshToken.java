package com.justrocketscience.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code refresh_tokens} table.
 *
 * Schema owner: jrs-auth-service (V2__create_refresh_tokens.sql).
 *
 * Critical design choices:
 *
 * 1. NO @ManyToOne to User.
 *    Stores user_id as a plain UUID column, not a JPA relationship.
 *    Reason: Avoids accidental eager/lazy loading of the User entity
 *    when all we need is the UUID for validation.
 *    A Feign call to look up a user would be wasteful here;
 *    UserRepository.findById() is called explicitly when needed.
 *    Pattern: "Aggregate boundary" — RefreshToken is its own aggregate.
 *
 * 2. No bidirectional relationship on User.
 *    User has no List<RefreshToken> collection.
 *    Reason: Loading a User would never need all their tokens.
 *    RefreshTokenRepository queries by user_id directly.
 *    Bidirectional collections are a common JPA performance trap.
 *
 * 3. Stores token HASH, not raw token.
 *    Raw token lives only in memory and the HTTP response.
 *    See JwtService for SHA-256 hashing logic.
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        // Mirrors indexes from V2__create_refresh_tokens.sql.
        // Hibernate schema validation (ddl-auto=validate) checks
        // these exist. Flyway still owns actual creation.
        @Index(name = "idx_rt_token_hash",    columnList = "token_hash"),
        @Index(name = "idx_rt_user_id",       columnList = "user_id"),
        @Index(name = "idx_rt_expires_at",    columnList = "expires_at"),
        @Index(name = "idx_rt_active_by_user",columnList = "user_id, expires_at DESC")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RefreshToken {

    // ─────────────────────────────────────────────────────────
    // PRIMARY KEY
    // ─────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
        name = "id",
        columnDefinition = "UUID",
        updatable = false,
        nullable = false
    )
    private UUID id;

    // ─────────────────────────────────────────────────────────
    // OWNER REFERENCE
    // ─────────────────────────────────────────────────────────

    /**
     * ID of the user who owns this token.
     *
     * Plain UUID — intentionally NOT a @ManyToOne User reference.
     * See class-level javadoc for full rationale.
     *
     * updatable = false: owner of a token never changes.
     * If a user wants a new token, a new row is created.
     */
    @Column(
        name = "user_id",
        columnDefinition = "UUID",
        nullable = false,
        updatable = false
    )
    private UUID userId;

    // ─────────────────────────────────────────────────────────
    // TOKEN HASH
    // ─────────────────────────────────────────────────────────

    /**
     * SHA-256 hex digest of the raw refresh token string.
     *
     * length = 255: SHA-256 produces 64 hex chars.
     *   255 allows future algorithm change without migration.
     *
     * updatable = false: the hash is written once on token creation.
     *   On refresh, the OLD row is revoked and a NEW row is inserted
     *   (token rotation). The hash never changes on a given row.
     *
     * unique = true: mirrors CONSTRAINT uq_refresh_tokens_token_hash.
     *   Prevents duplicate token storage (astronomically unlikely
     *   with SHA-256 of random tokens, but enforced at DB level).
     */
    @Column(
        name = "token_hash",
        length = 255,
        nullable = false,
        unique = true,
        updatable = false
    )
    private String tokenHash;

    // ─────────────────────────────────────────────────────────
    // DEVICE INFO
    // ─────────────────────────────────────────────────────────

    /**
     * User-Agent string captured at login time.
     *
     * Nullable — not all clients send User-Agent.
     * Used for: "You are logged in from Chrome on Mac" display.
     * Not used for security decisions (User-Agent is trivially spoofed).
     *
     * length = 500: covers most real User-Agent strings.
     *   Chrome on Windows: ~120 chars.
     *   Some crawler agents: ~300 chars.
     *   500 is a safe ceiling.
     *
     * updatable = false: captured at login, never changed.
     */
    @Column(
        name = "device_info",
        length = 500,
        updatable = false
    )
    private String deviceInfo;

    // ─────────────────────────────────────────────────────────
    // EXPIRY
    // ─────────────────────────────────────────────────────────

    /**
     * Hard expiry timestamp for this token (UTC).
     *
     * Set by JwtService: Instant.now().plusMillis(refreshTokenExpiry).
     * Default: 7 days from creation.
     *
     * Both the JWT payload AND this column carry the expiry.
     *   JWT expiry → fast stateless check (no DB hit needed).
     *   DB expiry  → source of truth for cleanup jobs and
     *               authoritative validation in theft scenarios.
     *
     * updatable = false: expiry is set at creation, never extended.
     *   Extending expiry would be a security risk (stolen token
     *   would silently get a longer life).
     */
    @Column(
        name = "expires_at",
        nullable = false,
        updatable = false
    )
    private Instant expiresAt;

    // ─────────────────────────────────────────────────────────
    // REVOCATION FLAG
    // ─────────────────────────────────────────────────────────

    /**
     * Whether this token has been explicitly revoked.
     *
     * false (default) = valid, usable token.
     * true  = revoked; any use is rejected AND flagged as
     *         potential token theft.
     *
     * Set to true by:
     *   a) Logout (single-device): revoke this specific token.
     *   b) Logout all: revoke all tokens for user_id.
     *   c) Password change: revoke all tokens for user_id.
     *   d) Token refresh (rotation): revoke old token, issue new row.
     *
     * NOT deleted on revocation — see V2 migration for rationale
     * (theft detection requires the row to persist briefly).
     * A cleanup job deletes revoked + expired rows nightly.
     *
     * @Builder.Default: required so Builder initializes to false,
     * not null (Boolean vs boolean — null would fail DB NOT NULL).
     */
    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = Boolean.FALSE;

    // ─────────────────────────────────────────────────────────
    // AUDIT
    // ─────────────────────────────────────────────────────────

    /**
     * Token creation timestamp (UTC).
     *
     * No updatedAt — this entity is effectively write-once.
     * The only mutable field is {@code revoked}.
     * Tracking "when was it revoked" is implicit: if revoked=true
     * and you need the revocation time, add a revoked_at column
     * in a future migration. Not needed for Phase 1.
     */
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    // ─────────────────────────────────────────────────────────
    // LIFECYCLE CALLBACKS
    // ─────────────────────────────────────────────────────────

    /**
     * Sets createdAt before INSERT.
     * expiresAt is set by the service layer (JwtService),
     * not here — the service controls token lifetime config.
     */
    @PrePersist
    protected void onPersist() {
        this.createdAt = Instant.now();
    }

    // ─────────────────────────────────────────────────────────
    // BUSINESS METHODS
    // ─────────────────────────────────────────────────────────

    /**
     * Returns true if this token is still valid for use.
     *
     * A token is valid when ALL of the following are true:
     *   1. Not revoked (explicit logout/rotation hasn't fired)
     *   2. Not expired (wall clock hasn't passed expiresAt)
     *
     * Called by AuthService.refresh() after loading the row.
     * Encapsulates validation logic in the entity itself
     * (rich domain model) rather than scattering it in service code.
     *
     * @return true if the token can be used for a refresh operation
     */
    public boolean isValid() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }

    /**
     * Returns true if this token has expired by wall clock,
     * regardless of the revoked flag.
     *
     * Used by the cleanup scheduler to identify rows eligible
     * for deletion even if they were never explicitly revoked.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Marks this token as revoked.
     *
     * Convenience method — caller doesn't need to know the field name.
     * Always call tokenRepository.save(token) after this.
     */
    public void revoke() {
        this.revoked = Boolean.TRUE;
    }

    // ─────────────────────────────────────────────────────────
    // EQUALS / HASHCODE
    // ─────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 31;
    }
}
