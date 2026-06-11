package com.justrocketscience.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code users} table.
 *
 * Schema owner: jrs-auth-service (V1__create_users.sql).
 * No other service references this entity class directly —
 * other services store user_id as a plain UUID column.
 *
 * Lombok strategy:
 *   @Getter + @Setter over @Data — @Data generates equals/hashCode
 *   using all fields, which causes issues with JPA proxies and
 *   bidirectional relationships. Explicit equals/hashCode on UUID
 *   id is the correct JPA pattern.
 *
 *   @Builder requires @NoArgsConstructor + @AllArgsConstructor
 *   when both are present. JPA mandates a no-arg constructor;
 *   @Builder needs all-arg. Both must be declared explicitly.
 *
 * Spring Security:
 *   This entity is NOT UserDetails. UserDetailsServiceImpl
 *   maps it to a Spring Security UserDetails object on load.
 *   Keeps the entity free of Spring Security coupling.
 */
@Entity
@Table(
    name = "users",
    // Mirrors the named constraints in V1__create_users.sql.
    // These allow Hibernate schema validation (ddl-auto=validate)
    // to confirm the constraints exist without managing them.
    // They do NOT create the constraints — Flyway owns schema creation.
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_email",    columnNames = "email"),
        @UniqueConstraint(name = "uq_users_username", columnNames = "username")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "passwordHash") // NEVER log the password hash
public class User {

    // ─────────────────────────────────────────────────────────
    // PRIMARY KEY
    // ─────────────────────────────────────────────────────────

    /**
     * UUID v4 primary key.
     *
     * GenerationType.UUID: Spring Boot 3 / Hibernate 6 native strategy.
     * Hibernate generates the UUID in Java (not via DB DEFAULT)
     * before the INSERT, so the entity has its id immediately
     * after calling save() — no extra SELECT needed.
     *
     * columnDefinition = "UUID": tells Hibernate to use PostgreSQL's
     * native UUID column type, not VARCHAR. This ensures:
     *   - Correct pg_dump output
     *   - Proper indexing (UUID ops, not string comparison)
     *   - Correct JDBC type mapping (no manual conversion)
     *
     * updatable = false: PK must never change after creation.
     */
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
    // IDENTITY FIELDS
    // ─────────────────────────────────────────────────────────

    /**
     * Unique display name.
     *
     * length = 50: mirrors VARCHAR(50) in V1 migration.
     * Hibernate validates length before attempting INSERT —
     * catches violations before the DB round-trip.
     *
     * updatable = false: username is immutable after registration.
     * To change username, a new account must be created.
     * This is a business rule, not just a DB constraint.
     * Immutability also means username can be safely cached.
     */
    @Column(
        name = "username",
        length = 50,
        nullable = false,
        updatable = false
    )
    private String username;

    /**
     * Unique email address. Used as the login identifier.
     *
     * length = 150: mirrors VARCHAR(150) in V1.
     * Email CAN be updated (change email flow) — no updatable=false.
     */
    @Column(
        name = "email",
        length = 150,
        nullable = false
    )
    private String email;

    /**
     * BCrypt hash of the user's password.
     *
     * length = 255: BCrypt = 60 chars. 255 allows future
     * migration to Argon2 (97 chars) without schema change.
     *
     * @ToString excludes this field (see class annotation).
     * Never appears in logs, JSON serialization (no @JsonProperty
     * in entity — DTOs handle serialization), or debug output.
     */
    @Column(
        name = "password_hash",
        length = 255,
        nullable = false
    )
    private String passwordHash;

    // ─────────────────────────────────────────────────────────
    // ROLE
    // ─────────────────────────────────────────────────────────

    /**
     * User's role in the system.
     *
     * @Enumerated(EnumType.STRING): stores literal enum name
     * ("USER" or "ADMIN") in the DB column.
     *
     * NEVER use EnumType.ORDINAL — if a new value is inserted
     * between USER and ADMIN, all existing ADMIN rows become
     * the new value silently. EnumType.STRING is immune to
     * reordering.
     *
     * columnDefinition = "user_role": references the PostgreSQL
     * ENUM type created in V1. Without this, Hibernate maps
     * to VARCHAR, which technically works but bypasses the PG
     * ENUM constraint.
     */
    @Enumerated(EnumType.STRING)
    @Column(
        name = "role",
        columnDefinition = "user_role",
        nullable = false
    )
    @Builder.Default
    private UserRole role = UserRole.USER;

    // ─────────────────────────────────────────────────────────
    // STATUS
    // ─────────────────────────────────────────────────────────

    /**
     * Account active status.
     *
     * false = soft-deleted / banned account.
     * Checked in UserDetailsServiceImpl.isEnabled().
     * Spring Security rejects disabled accounts before
     * the password check — returning 401 with "User is disabled".
     *
     * @Builder.Default: required when using @Builder with
     * a field that has a default value. Without it, @Builder
     * ignores the field initializer and sets null/false.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = Boolean.TRUE;

    // ─────────────────────────────────────────────────────────
    // AUDIT TIMESTAMPS
    // ─────────────────────────────────────────────────────────

    /**
     * Account creation timestamp (UTC).
     *
     * Instant maps to TIMESTAMPTZ in PostgreSQL via Hibernate 6.
     * updatable = false: set once in @PrePersist, never changed.
     *
     * Why Instant over LocalDateTime?
     *   Instant is always UTC — no timezone conversion possible.
     *   LocalDateTime has no timezone info — dangerous when the
     *   JVM timezone differs from the DB server timezone.
     */
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    /**
     * Last modification timestamp (UTC).
     * Updated in @PreUpdate on every write.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ─────────────────────────────────────────────────────────
    // JPA LIFECYCLE CALLBACKS
    // ─────────────────────────────────────────────────────────

    /**
     * Called by Hibernate immediately before INSERT.
     * Sets both timestamps to the current UTC instant.
     *
     * Why @PrePersist instead of @CreationTimestamp?
     *   @CreationTimestamp is a Hibernate-specific annotation.
     *   @PrePersist is standard JPA — no vendor lock-in.
     *   Also: @PrePersist lets us set multiple fields in one
     *   place and add business logic if needed.
     */
    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Called by Hibernate immediately before UPDATE.
     * Keeps updatedAt current without manual service-layer calls.
     *
     * Note: @PreUpdate fires on every flush of a dirty entity,
     * including partial updates. This is intentional — any field
     * change should update the timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ─────────────────────────────────────────────────────────
    // EQUALS / HASHCODE
    // ─────────────────────────────────────────────────────────

    /**
     * Equals based solely on id (UUID).
     *
     * JPA best practice: equals/hashCode on the business key
     * or surrogate key only. Never on mutable fields.
     *
     * Why not use @EqualsAndHashCode(of = "id") from Lombok?
     *   It would be simpler, but Lombok's generated equals
     *   doesn't handle null id (transient entity before persist)
     *   correctly in all Hibernate proxy scenarios.
     *   Manual implementation is explicit and safe.
     *
     * Two User objects with null ids are NOT equal to each other —
     * each represents a distinct unsaved entity.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Fixed constant for null id (transient entity).
        // Stable hashCode required by Set/HashMap contracts —
        // must not change when id is assigned after persist.
        return id != null ? id.hashCode() : 31;
    }
}
