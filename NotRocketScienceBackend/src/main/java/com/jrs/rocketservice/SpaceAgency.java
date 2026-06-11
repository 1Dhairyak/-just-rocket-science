package com.jrs.rocketservice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JPA entity representing a space agency.
 *
 * Maps to the {@code space_agencies} table defined in V1__create_space_agencies.sql.
 *
 * Lombok notes:
 * - @Getter / @Setter used instead of @Data to allow manual equals/hashCode.
 * - @ToString excludes the rockets collection to prevent lazy-loading surprises
 *   and infinite recursion (Rocket.toString → SpaceAgency.toString → ...).
 * - @NoArgsConstructor satisfies the JPA spec requirement for a no-arg constructor.
 */
@Entity
@Table(name = "space_agencies")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "rockets")
public class SpaceAgency {

    // -------------------------------------------------------------------------
    // Primary key
    // -------------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -------------------------------------------------------------------------
    // Core fields
    // -------------------------------------------------------------------------

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String website;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    // -------------------------------------------------------------------------
    // Audit timestamps
    //
    // @CreationTimestamp  — Hibernate sets this once on INSERT; never updated.
    // @UpdateTimestamp    — Hibernate refreshes this on every UPDATE.
    // updatable = false   — prevents JPA from overwriting createdAt after insert.
    // -------------------------------------------------------------------------

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // Relationships
    //
    // FetchType.LAZY  — rockets are NOT loaded when an agency is fetched.
    //                   Only loaded if getRockets() is called inside a transaction.
    //                   Prevents accidental N+1 loads on list endpoints.
    //
    // CascadeType.NONE (default) — agency operations do NOT cascade to rockets.
    //                   Rockets must be saved and deleted explicitly.
    //                   Matches ON DELETE RESTRICT in the database FK.
    //
    // orphanRemoval = false (default) — removing a Rocket from this list does
    //                   NOT delete it from the database. Explicit is safer.
    //
    // mappedBy = "agency" — SpaceAgency is the inverse (non-owning) side.
    //                   The FK column lives on the rockets table, not here.
    // -------------------------------------------------------------------------

    @OneToMany(
            mappedBy = "agency",
            fetch = FetchType.LAZY
    )
    private List<Rocket> rockets = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Convenience constructor (excludes managed fields)
    // -------------------------------------------------------------------------

    public SpaceAgency(String name, String country) {
        this.name = name;
        this.country = country;
    }

    // -------------------------------------------------------------------------
    // equals / hashCode
    //
    // Rule: use the business key (name), not the surrogate id.
    //
    // Why not use id?
    //   A transient entity (not yet persisted) has id == null. Two new
    //   SpaceAgency objects with the same name would be unequal by id but
    //   represent the same agency. Using name is semantically correct.
    //
    // Why not use @EqualsAndHashCode from Lombok?
    //   Lombok's default includes all fields, which pulls in the rockets
    //   collection and causes a LazyInitializationException or infinite loop.
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpaceAgency other)) return false;
        return name != null && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
