package com.jrs.rocketservice;

/**
 * Lifecycle status of a launch vehicle.
 *
 * Stored in the database as a VARCHAR string (e.g. "ACTIVE") via
 * {@code @Enumerated(EnumType.STRING)} on the Rocket entity.
 *
 * Matches the CHECK constraint in V2__create_rockets.sql:
 *   CHECK (status IN ('ACTIVE', 'RETIRED', 'IN_DEVELOPMENT'))
 */
public enum RocketStatus {

    /** Rocket is currently in active service. */
    ACTIVE,

    /** Rocket has been permanently retired from flight. */
    RETIRED,

    /** Rocket is under development and has not yet flown operationally. */
    IN_DEVELOPMENT
}
