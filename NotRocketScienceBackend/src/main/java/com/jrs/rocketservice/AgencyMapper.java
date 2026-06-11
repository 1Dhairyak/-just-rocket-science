package com.jrs.rocketservice;

import com.jrs.rocketservice.SpaceAgency;
import com.jrs.rocketservice.AgencyResponse;
import com.jrs.rocketservice.AgencySummaryResponse;
import com.jrs.rocketservice.RocketSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.Collections;
import java.util.List;

/**
 * MapStruct mapper: SpaceAgency entity → Agency DTOs.
 *
 * componentModel = "spring"
 *   → MapStruct generates an @Component class. Spring injects it like any
 *     other bean via @Autowired or constructor injection.
 *
 * unmappedTargetPolicy = ReportingPolicy.ERROR
 *   → If any field in the target DTO is not covered by a mapping rule,
 *     compilation fails. No silent null fields in the response.
 *
 * nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT
 *   → If the source collection (rockets) is null, MapStruct returns an
 *     empty list instead of null. Consumers can always safely iterate.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT
)
public interface AgencyMapper {

    /**
     * Maps a SpaceAgency to a lightweight AgencySummaryResponse.
     *
     * All field names match exactly between entity and record — no @Mapping needed.
     * MapStruct resolves: id, name, country, foundedYear, logoUrl.
     *
     * Used by: RocketMapper (nested inside RocketResponse)
     */
    AgencySummaryResponse toSummaryResponse(SpaceAgency agency);

    /**
     * Maps a SpaceAgency to a full AgencyResponse, including the rocket list.
     *
     * The rockets field on SpaceAgency is List<Rocket>.
     * The rockets field on AgencyResponse is List<RocketSummaryResponse>.
     *
     * MapStruct handles the list conversion automatically by calling
     * rocketToSummaryResponse() (defined below) on each element.
     *
     * If agency.getRockets() is null (LAZY not initialized), the
     * nullValueIterableMappingStrategy above returns an empty list instead
     * of null — matching the API contract.
     */
    AgencyResponse toResponse(SpaceAgency agency);

    /**
     * Maps a single Rocket to a RocketSummaryResponse.
     *
     * This method is used internally by toResponse() when converting the
     * rockets collection. It is package-visible so RocketMapper can also
     * delegate to AgencyMapper for the inverse direction.
     *
     * All field names match — no @Mapping annotations needed.
     */
    RocketSummaryResponse rocketToSummaryResponse(com.jrs.rocketservice.Rocket rocket);

    /**
     * Converts a null rockets collection to an empty list.
     *
     * Called by toResponse() if agency.getRockets() is null.
     * The nullValueIterableMappingStrategy handles element-level nulls;
     * this default method handles the case where the entire list reference is null.
     *
     * Declared as a default method so MapStruct can use it during code generation
     * without needing a separate implementation class.
     */
    default List<RocketSummaryResponse> toRocketSummaryList(
            List<com.jrs.rocketservice.Rocket> rockets) {
        if (rockets == null) {
            return Collections.emptyList();
        }
        return rockets.stream()
                .map(this::rocketToSummaryResponse)
                .toList();
    }
}
