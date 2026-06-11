package com.jrs.rocketservice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RocketResponse(
        Long id,
        AgencySummaryResponse agency,
        String name,
        String status,
        BigDecimal height,
        BigDecimal diameter,
        BigDecimal mass,
        @JsonProperty("payloadToLeo") BigDecimal payloadToLeo,
        @JsonProperty("thrustKn") Double thrustKn,
        @JsonProperty("reusable") Boolean reusable,
        @JsonProperty("humanCrewCapacity") Integer humanCrewCapacity,
        @JsonProperty("numberOfStages") Integer numberOfStages,
        @JsonProperty("firstLaunchDate") LocalDate firstLaunchDate,
        String description,
        @JsonProperty("imageUrl") String imageUrl,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {}
