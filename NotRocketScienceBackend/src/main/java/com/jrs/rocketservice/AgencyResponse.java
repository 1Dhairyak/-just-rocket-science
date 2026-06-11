package com.jrs.rocketservice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Full agency representation for single-resource endpoints.
 *
 * Used by: GET /agencies/{id}
 *
 * Includes all agency fields plus a list of rocket summaries.
 * Rockets are RocketSummaryResponse — not RocketResponse — because embedding
 * full rocket detail (including nested agency again) inside an agency response
 * would be circular and wasteful.
 *
 * Audit timestamps (createdAt, updatedAt) are included for completeness.
 * Front-end clients can display "last updated" metadata if desired.
 */
public record AgencyResponse(

        Long id,

        String name,

        String country,

        @JsonProperty("foundedYear")
        Integer foundedYear,

        String description,

        String website,

        @JsonProperty("logoUrl")
        String logoUrl,

        /**
         * Lightweight list of rockets owned by this agency.
         * Uses RocketSummaryResponse — not the full RocketResponse — to keep
         * the payload flat and avoid nesting agency info inside each rocket
         * inside the agency response.
         *
         * An empty list (not null) is returned when the agency has no rockets.
         */
        List<RocketSummaryResponse> rockets,

        /**
         * ISO-8601 UTC timestamp: "2024-01-15T10:30:00Z"
         * Jackson serializes Instant as a Unix epoch number by default.
         * The global Jackson config (Step 8) will override this to ISO-8601 strings.
         * @JsonProperty is not needed here — "createdAt" matches camelCase.
         */
        @JsonProperty("createdAt")
        Instant createdAt,

        @JsonProperty("updatedAt")
        Instant updatedAt
) {}
