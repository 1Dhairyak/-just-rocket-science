package com.jrs.rocketservice;

import com.jrs.rocketservice.Rocket;
import com.jrs.rocketservice.RocketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Rocket} entities.
 *
 * All list methods return {@code Page<Rocket>} — never a raw List.
 * Callers control page size, page number, and sort via a {@code Pageable} argument.
 *
 * LAZY loading note:
 *   All queries here load Rocket rows only. The agency association is LAZY —
 *   it is NOT loaded unless a method uses JOIN FETCH.
 *   Service layer DTOs that need agency data must use the *WithAgency variants,
 *   or call a separate agency lookup.
 */
@Repository
public interface RocketRepository extends JpaRepository<Rocket, Long> {

    /**
     * Returns a paginated list of all rockets.
     *
     * Derived from JpaRepository — no implementation needed.
     *
     * SQL equivalent:
     *   SELECT * FROM rockets ORDER BY ? LIMIT ? OFFSET ?;
     */
    Page<Rocket> findAll(Pageable pageable);

    /**
     * Returns a paginated list of rockets filtered by lifecycle status.
     *
     * Uses the idx_rockets_status index defined in V2__create_rockets.sql.
     *
     * SQL equivalent:
     *   SELECT * FROM rockets WHERE status = ? ORDER BY ? LIMIT ? OFFSET ?;
     */
    Page<Rocket> findByStatus(RocketStatus status, Pageable pageable);

    /**
     * Returns a paginated list of rockets belonging to a specific agency.
     *
     * Uses the idx_rockets_agency_id index defined in V2__create_rockets.sql.
     *
     * SQL equivalent:
     *   SELECT * FROM rockets WHERE agency_id = ? ORDER BY ? LIMIT ? OFFSET ?;
     */
    Page<Rocket> findByAgencyId(Long agencyId, Pageable pageable);

    /**
     * Returns a paginated list of rockets filtered by both agency and status.
     *
     * Uses the composite idx_rockets_agency_id_status index — the most efficient
     * path for the GET /agencies/{id}/rockets?status=ACTIVE query pattern.
     *
     * SQL equivalent:
     *   SELECT * FROM rockets WHERE agency_id = ? AND status = ?
     *   ORDER BY ? LIMIT ? OFFSET ?;
     */
    Page<Rocket> findByAgencyIdAndStatus(Long agencyId, RocketStatus status, Pageable pageable);

    /**
     * Checks whether a rocket with the given name already exists under a specific agency.
     *
     * Reflects the UNIQUE(agency_id, name) constraint in the schema.
     * Returns a boolean — no entity loaded into memory.
     *
     * SQL equivalent:
     *   SELECT COUNT(*) > 0 FROM rockets WHERE LOWER(name) = LOWER(?) AND agency_id = ?;
     */
    boolean existsByNameIgnoreCaseAndAgencyId(String name, Long agencyId);

    /**
     * Fetches a single rocket together with its agency in one query.
     *
     * WHY @Query here:
     *   A plain findById() returns a Rocket with a LAZY agency proxy.
     *   Any service code that accesses rocket.getAgency().getName() outside
     *   a transaction will throw LazyInitializationException.
     *   JOIN FETCH loads both in one round-trip — used for GET /rockets/{id}.
     *
     * SQL equivalent:
     *   SELECT r FROM rockets r JOIN FETCH r.agency WHERE r.id = ?;
     */
    @Query("SELECT r FROM Rocket r JOIN FETCH r.agency WHERE r.id = :id")
    java.util.Optional<Rocket> findByIdWithAgency(Long id);

    /**
     * Returns a paginated list of rockets with their agencies pre-fetched.
     *
     * WHY @Query here:
     *   Iterating over a Page<Rocket> and calling rocket.getAgency() for each row
     *   fires one SELECT per rocket — a classic N+1 problem.
     *   JOIN FETCH collapses all agency data into the original query.
     *
     * WHY the countQuery:
     *   Spring Data cannot automatically derive the COUNT query when JOIN FETCH
     *   is present (the FETCH clause confuses the count projection). A separate
     *   countQuery without the FETCH is required for correct pagination totals.
     *
     * Used by: GET /rockets (when agency name is included in the response DTO)
     */
    @Query(
        value      = "SELECT r FROM Rocket r JOIN FETCH r.agency",
        countQuery = "SELECT COUNT(r) FROM Rocket r"
    )
    Page<Rocket> findAllWithAgency(Pageable pageable);
}
