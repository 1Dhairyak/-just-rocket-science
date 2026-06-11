package com.jrs.rocketservice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-11T08:17:09+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.7 (Eclipse Adoptium)"
)
@Component
public class RocketMapperImpl implements RocketMapper {

    @Autowired
    private AgencyMapper agencyMapper;

    @Override
    public RocketSummaryResponse toSummaryResponse(Rocket rocket) {
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

    @Override
    public RocketResponse toResponse(Rocket rocket) {
        if ( rocket == null ) {
            return null;
        }

        AgencySummaryResponse agency = null;
        Long id = null;
        String name = null;
        String status = null;
        BigDecimal height = null;
        BigDecimal diameter = null;
        BigDecimal mass = null;
        BigDecimal payloadToLeo = null;
        Double thrustKn = null;
        Boolean reusable = null;
        Integer humanCrewCapacity = null;
        Integer numberOfStages = null;
        LocalDate firstLaunchDate = null;
        String description = null;
        String imageUrl = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        agency = agencyMapper.toSummaryResponse( rocket.getAgency() );
        id = rocket.getId();
        name = rocket.getName();
        status = rocket.getStatus();
        height = rocket.getHeight();
        diameter = rocket.getDiameter();
        mass = rocket.getMass();
        payloadToLeo = rocket.getPayloadToLeo();
        thrustKn = rocket.getThrustKn();
        reusable = rocket.getReusable();
        humanCrewCapacity = rocket.getHumanCrewCapacity();
        numberOfStages = rocket.getNumberOfStages();
        firstLaunchDate = rocket.getFirstLaunchDate();
        description = rocket.getDescription();
        imageUrl = rocket.getImageUrl();
        createdAt = rocket.getCreatedAt();
        updatedAt = rocket.getUpdatedAt();

        RocketResponse rocketResponse = new RocketResponse( id, agency, name, status, height, diameter, mass, payloadToLeo, thrustKn, reusable, humanCrewCapacity, numberOfStages, firstLaunchDate, description, imageUrl, createdAt, updatedAt );

        return rocketResponse;
    }
}
