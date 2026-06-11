package com.jrs.rocketservice;

import com.jrs.rocketservice.SpaceAgency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link SpaceAgency} entities.
 *
 * Extends JpaRepository which provides:
 *   save(), findById(), findAll(), delete(), count(), existsById(), and more.
 *
 * All list methods return {@code Page<SpaceAgency>} — never a raw List.
 * Callers control page size, page number, and sort via a {@code Pageable} argument.
 */
@Repository
public interface AgencyRepository extends JpaRepository<SpaceAgency, Long> {

    /**
     * Returns a paginated list of all agencies.
     *
     * Derived from JpaRepository — no implementation needed.
     * Supports any sort field and direction via the Pageable argument.
     *
     * Example: Pageable.of(0, 10, Sort.by("name"))
     *
     * SQL equivalent:
     *   SELECT * FROM space_agencies ORDER BY name ASC LIMIT 10 OFFSET 0;
     */
    Page<SpaceAgency> findAll(Pageable pageable);

    /**
     * Returns a paginated list of agencies filtered by country.
     *
     * Case-insensitive: "united states", "United States", "UNITED STATES" all match.
     * Uses the idx_space_agencies_country index defined in V1__create_space_agencies.sql.
     *
     * SQL equivalent:
     *   SELECT * FROM space_agencies WHERE LOWER(country) = LOWER(?) ...
     */
    Page<SpaceAgency> findByCountryIgnoreCase(String country, Pageable pageable);

    /**
     * Checks whether an agency with the given name already exists.
     *
     * Returns a boolean — no entity is loaded into memory.
     * Used as a lightweight existence check before insert operations.
     *
     * SQL equivalent:
     *   SELECT COUNT(*) > 0 FROM space_agencies WHERE LOWER(name) = LOWER(?);
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Fetches an agency together with its rockets in a single SQL query.
     *
     * WHY @Query here:
     *   A plain findById() loads the agency but leaves rockets LAZY.
     *   Calling agency.getRockets() afterward triggers a second SELECT — an N+1.
     *   JOIN FETCH collapses both into one query when rockets are needed.
     *
     * Used by: GET /agencies/{id}/rockets
     *
     * SQL equivalent:
     *   SELECT a FROM space_agencies a
     *   LEFT JOIN FETCH rockets r ON r.agency_id = a.id
     *   WHERE a.id = ?;
     */
    @Query("SELECT a FROM SpaceAgency a LEFT JOIN FETCH a.rockets WHERE a.id = :id")
    java.util.Optional<SpaceAgency> findByIdWithRockets(Long id);
}
