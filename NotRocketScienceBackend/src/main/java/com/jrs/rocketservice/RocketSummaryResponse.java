package com.jrs.rocketservice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RocketSummaryResponse(
        Long id,
        String name,
        String status,
        @JsonProperty("imageUrl")
        String imageUrl,
        @JsonProperty("payloadToLeo")
        BigDecimal payloadToLeo,
        @JsonProperty("numberOfStages")
        Integer numberOfStages,
        @JsonProperty("firstLaunchDate")
        LocalDate firstLaunchDate
) {}
