package com.jrs.rocketservice;

import com.jrs.rocketservice.Rocket;
import com.jrs.rocketservice.RocketResponse;
import com.jrs.rocketservice.RocketSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper: Rocket entity → Rocket DTOs.
 *
 * componentModel = "spring"
 *   → Spring-managed bean, injectable via constructor.
 *
 * unmappedTargetPolicy = ReportingPolicy.ERROR
 *   → Every field in every target DTO must be explicitly covered.
 *     Adding a field to a DTO without updating the mapper breaks compilation.
 *
 * uses = AgencyMapper.class
 *   → Tells MapStruct to delegate nested agency mapping to AgencyMapper.
 *     When toResponse() needs to map rocket.agency → AgencySummaryResponse,
 *     it calls agencyMapper.toSummaryResponse(rocket.getAgency()) automatically.
 *     No manual null-checking or delegation needed in the service layer.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = AgencyMapper.class
)
public interface RocketMapper {

    /**
     * Maps a Rocket to a lightweight RocketSummaryResponse.
     *
     * All field names match — no @Mapping annotations needed.
     * MapStruct resolves: id, name, status, imageUrl.
     *
     * Used by: GET /rockets (list), GET /agencies/{id}/rockets (list),
     *          AgencyMapper.toResponse() (embedded list).
     */
    RocketSummaryResponse toSummaryResponse(Rocket rocket);

    /**
     * Maps a Rocket to a full RocketResponse, including nested agency summary.
     *
     * Field name differences between entity and DTO:
     *
     *   rocket.agency       → response.agency  (SpaceAgency → AgencySummaryResponse)
     *                          Delegated to AgencyMapper.toSummaryResponse() via uses=.
     *
     * All other fields match by name:
     *   id, name, status, height, diameter, mass, payloadToLeo,
     *   firstLaunchDate, description, imageUrl, createdAt, updatedAt.
     *
     * Nullable fields (height, diameter, mass, payloadToLeo, firstLaunchDate,
     * imageUrl) — MapStruct passes null through directly. The record constructor
     * accepts null; Jackson serializes it as JSON null.
     *
     * @Mapping not required here because all field names already match.
     * Kept as documentation that the agency nested mapping is intentional.
     */
    @Mapping(target = "agency", source = "agency")
    RocketResponse toResponse(Rocket rocket);
}
