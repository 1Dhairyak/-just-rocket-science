package com.jrs.rocketservice;

import com.jrs.rocketservice.Rocket;
import com.jrs.rocketservice.RocketStatus;
import com.jrs.rocketservice.RocketResponse;
import com.jrs.rocketservice.RocketSummaryResponse;
import com.jrs.rocketservice.exception.ResourceNotFoundException;
import com.jrs.rocketservice.RocketMapper;
import com.jrs.rocketservice.RocketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Rocket read operations.
 *
 * All methods are @Transactional(readOnly = true) — same rationale as AgencyService.
 */
@Service
@Transactional(readOnly = true)
public class RocketService {

    private final RocketRepository rocketRepository;
    private final RocketMapper rocketMapper;

    public RocketService(RocketRepository rocketRepository, RocketMapper rocketMapper) {
        this.rocketRepository = rocketRepository;
        this.rocketMapper = rocketMapper;
    }

    // -------------------------------------------------------------------------
    // GET /rockets
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of rocket summaries, with optional filtering
     * by status and/or agency.
     *
     * Filtering strategy — four combinations, each maps to a specific repository
     * method and index:
     *
     *   status=null,  agencyId=null  → findAllWithAgency(pageable)
     *   status=set,   agencyId=null  → findByStatus(status, pageable)
     *   status=null,  agencyId=set   → findByAgencyId(agencyId, pageable)
     *   status=set,   agencyId=set   → findByAgencyIdAndStatus(agencyId, status, pageable)
     *
     * WHY findAllWithAgency for the unfiltered path:
     *   The response DTO is RocketSummaryResponse which includes only
     *   id, name, status, and imageUrl — no agency fields. So for the list
     *   endpoint we do NOT need the agency JOIN FETCH. Using findAll() is
     *   correct and efficient here.
     *
     *   HOWEVER: if the summary DTO ever includes the agency name, swap
     *   findAll() for findAllWithAgency() to avoid N+1.
     *   This is explicitly documented here so the decision is visible.
     *
     * @param status   optional status filter
     * @param agencyId optional agency filter
     * @param pageable page, size, and sort
     * @return paginated rocket summaries
     */
    public Page<RocketSummaryResponse> getAllRockets(
            RocketStatus status, Long agencyId, Pageable pageable) {

        Page<Rocket> rockets;

        if (status == null && agencyId == null) {
            // No filters — return all rockets
            rockets = rocketRepository.findAll(pageable);

        } else if (status != null && agencyId == null) {
            // Status filter only
            rockets = rocketRepository.findByStatus(status, pageable);

        } else if (status == null) {
            // Agency filter only
            rockets = rocketRepository.findByAgencyId(agencyId, pageable);

        } else {
            // Both filters — uses composite index (agency_id, status)
            rockets = rocketRepository.findByAgencyIdAndStatus(agencyId, status, pageable);
        }

        return rockets.map(rocketMapper::toSummaryResponse);
    }

    // -------------------------------------------------------------------------
    // GET /rockets/{id}
    // -------------------------------------------------------------------------

    /**
     * Returns the full detail of a single rocket, including nested agency summary.
     *
     * Uses findByIdWithAgency() — a JOIN FETCH query that loads the rocket and
     * its agency in one SQL statement.
     *
     * WHY JOIN FETCH is required here:
     *   RocketResponse embeds an AgencySummaryResponse. The mapper calls
     *   rocket.getAgency() to build it. Without JOIN FETCH, the agency
     *   association is a LAZY proxy — accessing it outside an active Hibernate
     *   session throws LazyInitializationException. The JOIN FETCH ensures
     *   the agency is loaded within the same transaction as the rocket.
     *
     * Throws ResourceNotFoundException if the rocket does not exist → HTTP 404.
     *
     * @param id the rocket ID from the URL path
     * @return full rocket response with nested agency summary
     */
    public RocketResponse getRocketById(Long id) {
        Rocket rocket = rocketRepository.findByIdWithAgency(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rocket", id));

        return rocketMapper.toResponse(rocket);
    }
}
