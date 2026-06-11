package com.jrs.rocketservice;

import com.jrs.rocketservice.RocketStatus;
import com.jrs.rocketservice.SpaceAgency;
import com.jrs.rocketservice.AgencyResponse;
import com.jrs.rocketservice.AgencySummaryResponse;
import com.jrs.rocketservice.RocketSummaryResponse;
import com.jrs.rocketservice.exception.ResourceNotFoundException;
import com.jrs.rocketservice.AgencyMapper;
import com.jrs.rocketservice.RocketMapper;
import com.jrs.rocketservice.AgencyRepository;
import com.jrs.rocketservice.RocketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for SpaceAgency read operations.
 *
 * All methods are @Transactional(readOnly = true):
 *   - Tells Hibernate this session will not modify data.
 *   - Hibernate skips dirty-checking on all loaded entities (performance win).
 *   - Spring sets the JDBC connection to read-only mode.
 *   - On a replicated database, Spring can route reads to a read replica.
 *
 * Constructor injection only — dependencies are final, immutable, and
 * visible at a glance. No @Autowired on fields.
 */
@Service
@Transactional(readOnly = true)
public class AgencyService {

    private final AgencyRepository agencyRepository;
    private final RocketRepository rocketRepository;
    private final AgencyMapper agencyMapper;
    private final RocketMapper rocketMapper;

    public AgencyService(
            AgencyRepository agencyRepository,
            RocketRepository rocketRepository,
            AgencyMapper agencyMapper,
            RocketMapper rocketMapper) {
        this.agencyRepository = agencyRepository;
        this.rocketRepository = rocketRepository;
        this.agencyMapper = agencyMapper;
        this.rocketMapper = rocketMapper;
    }

    // -------------------------------------------------------------------------
    // GET /agencies
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of agency summaries, optionally filtered by country.
     *
     * Filtering strategy:
     *   country == null → return all agencies (no WHERE clause)
     *   country != null → filter by country, case-insensitive
     *
     * The controller passes a Pageable built from the request's ?page, ?size,
     * and ?sort parameters. This method simply forwards it to the repository.
     *
     * Returns Page<AgencySummaryResponse> — not Page<SpaceAgency>.
     * Entities never leave the service layer.
     *
     * @param country optional country filter (null means no filter)
     * @param pageable page, size, and sort from the request
     * @return paginated agency summaries
     */
    public Page<AgencySummaryResponse> getAllAgencies(String country, Pageable pageable) {
        Page<SpaceAgency> agencies = (country == null)
                ? agencyRepository.findAll(pageable)
                : agencyRepository.findByCountryIgnoreCase(country, pageable);

        return agencies.map(agencyMapper::toSummaryResponse);
    }

    // -------------------------------------------------------------------------
    // GET /agencies/{id}
    // -------------------------------------------------------------------------

    /**
     * Returns the full detail of a single agency, including its rocket list.
     *
     * Uses findByIdWithRockets() — a JOIN FETCH query that loads the agency
     * and its rockets collection in one SQL statement. This avoids two problems:
     *
     *   Problem 1 — LazyInitializationException:
     *     A plain findById() returns a SpaceAgency with a LAZY rockets proxy.
     *     Calling agency.getRockets() after the transaction closes (e.g. in
     *     the mapper or the controller) throws LazyInitializationException.
     *
     *   Problem 2 — N+1 query:
     *     Even inside a transaction, Hibernate would fire a second SELECT for
     *     the rockets collection. JOIN FETCH combines both into one query.
     *
     * Throws ResourceNotFoundException if the agency does not exist → HTTP 404.
     *
     * @param id the agency ID from the URL path
     * @return full agency response with embedded rocket summaries
     */
    public AgencyResponse getAgencyById(Long id) {
        SpaceAgency agency = agencyRepository.findByIdWithRockets(id)
                .orElseThrow(() -> new ResourceNotFoundException("SpaceAgency", id));

        return agencyMapper.toResponse(agency);
    }

    // -------------------------------------------------------------------------
    // GET /agencies/{agencyId}/rockets
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of rockets belonging to a specific agency,
     * optionally filtered by rocket status.
     *
     * Two-step approach:
     *   Step 1 — Verify the agency exists. If not, throw 404.
     *             A missing agency should return 404, not an empty list.
     *             An empty list implies "the agency exists but has no rockets".
     *   Step 2 — Query rockets with or without the status filter.
     *
     * Filtering strategy:
     *   status == null → return all rockets for the agency
     *   status != null → filter by status AND agency
     *
     * Uses the composite index (agency_id, status) in the status-filtered path.
     *
     * @param agencyId the agency ID from the URL path
     * @param status   optional status filter (null means no filter)
     * @param pageable page, size, and sort from the request
     * @return paginated rocket summaries for the agency
     */
    public Page<RocketSummaryResponse> getAgencyRockets(
            Long agencyId, RocketStatus status, Pageable pageable) {

        // Step 1: confirm the agency exists — plain existsById, no entity loaded
        if (!agencyRepository.existsById(agencyId)) {
            throw new ResourceNotFoundException("SpaceAgency", agencyId);
        }

        // Step 2: query rockets with or without status filter
        Page<com.jrs.rocketservice.Rocket> rockets = (status == null)
                ? rocketRepository.findByAgencyId(agencyId, pageable)
                : rocketRepository.findByAgencyIdAndStatus(agencyId, status, pageable);

        return rockets.map(rocketMapper::toSummaryResponse);
    }
}
