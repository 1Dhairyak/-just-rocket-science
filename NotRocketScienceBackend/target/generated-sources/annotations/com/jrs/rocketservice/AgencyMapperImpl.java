package com.jrs.rocketservice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-13T08:45:05+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.7 (Eclipse Adoptium)"
)
@Component
public class AgencyMapperImpl implements AgencyMapper {

    @Override
    public AgencySummaryResponse toSummaryResponse(SpaceAgency agency) {
        if ( agency == null ) {
            return null;
        }

        Long id = null;
        String name = null;
        String country = null;
        Integer foundedYear = null;
        String logoUrl = null;

        id = agency.getId();
        name = agency.getName();
        country = agency.getCountry();
        foundedYear = agency.getFoundedYear();
        logoUrl = agency.getLogoUrl();

        AgencySummaryResponse agencySummaryResponse = new AgencySummaryResponse( id, name, country, foundedYear, logoUrl );

        return agencySummaryResponse;
    }

    @Override
    public AgencyResponse toResponse(SpaceAgency agency) {
        if ( agency == null ) {
            return null;
        }

        Long id = null;
        String name = null;
        String country = null;
        Integer foundedYear = null;
        String description = null;
        String website = null;
        String logoUrl = null;
        List<RocketSummaryResponse> rockets = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        id = agency.getId();
        name = agency.getName();
        country = agency.getCountry();
        foundedYear = agency.getFoundedYear();
        description = agency.getDescription();
        website = agency.getWebsite();
        logoUrl = agency.getLogoUrl();
        rockets = toRocketSummaryList( agency.getRockets() );
        createdAt = agency.getCreatedAt();
        updatedAt = agency.getUpdatedAt();

        AgencyResponse agencyResponse = new AgencyResponse( id, name, country, foundedYear, description, website, logoUrl, rockets, createdAt, updatedAt );

        return agencyResponse;
    }

    @Override
    public RocketSummaryResponse rocketToSummaryResponse(Rocket rocket) {
        if ( rocket == null ) {
            return null;
        }

        Long id = null;
        String name = null;
        String status = null;
        String imageUrl = null;
        BigDecimal payloadToLeo = null;
        Integer numberOfStages = null;
        LocalDate firstLaunchDate = null;

        id = rocket.getId();
        name = rocket.getName();
        status = rocket.getStatus();
        imageUrl = rocket.getImageUrl();
        payloadToLeo = rocket.getPayloadToLeo();
        numberOfStages = rocket.getNumberOfStages();
        firstLaunchDate = rocket.getFirstLaunchDate();

        RocketSummaryResponse rocketSummaryResponse = new RocketSummaryResponse( id, name, status, imageUrl, payloadToLeo, numberOfStages, firstLaunchDate );

        return rocketSummaryResponse;
    }
}
