-- =============================================================================
-- V2__create_rockets.sql
-- Creates the rockets table with a FK to space_agencies.
-- Depends on: V1__create_space_agencies.sql
-- =============================================================================

-- Status domain for rockets.
-- Stored as VARCHAR, not a native PG ENUM, to avoid ALTER TYPE migrations
-- if values are added or renamed in the future.
CREATE TABLE rockets (
    id                BIGSERIAL       PRIMARY KEY,
    agency_id         BIGINT          NOT NULL,
    name              VARCHAR(100)    NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    height            DECIMAL(8, 2),  -- meters
    diameter          DECIMAL(8, 2),  -- meters
    mass              DECIMAL(12, 2), -- kg
    payload_to_leo    DECIMAL(10, 2), -- kg
    first_launch_date DATE,
    description       TEXT,
    image_url         VARCHAR(500),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Foreign key
    CONSTRAINT fk_rockets_agency
        FOREIGN KEY (agency_id)
        REFERENCES space_agencies (id)
        ON DELETE RESTRICT,     -- Never silently delete an agency that owns rockets

    -- Status must be one of the three defined values.
    -- New statuses (e.g. DECOMMISSIONED) require a migration to add the value here,
    -- keeping the constraint intentional and explicit.
    CONSTRAINT chk_rockets_status
        CHECK (status IN ('ACTIVE', 'RETIRED', 'IN_DEVELOPMENT')),

    -- Physical measurements must be positive when supplied.
    CONSTRAINT chk_rockets_height
        CHECK (height IS NULL OR height > 0),

    CONSTRAINT chk_rockets_diameter
        CHECK (diameter IS NULL OR diameter > 0),

    CONSTRAINT chk_rockets_mass
        CHECK (mass IS NULL OR mass > 0),

    CONSTRAINT chk_rockets_payload_to_leo
        CHECK (payload_to_leo IS NULL OR payload_to_leo > 0),

    -- No agency should have two rockets with the same name.
    CONSTRAINT uq_rockets_agency_name
        UNIQUE (agency_id, name),

    CONSTRAINT chk_rockets_image_url
        CHECK (image_url IS NULL OR image_url ~* '^https?://')
);

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------

-- FK lookup: used every time rockets are fetched by agency.
CREATE INDEX idx_rockets_agency_id
    ON rockets (agency_id);

-- Filter by status is a supported query param on both list endpoints.
CREATE INDEX idx_rockets_status
    ON rockets (status);

-- Composite: the most common list query is "rockets for agency X with status Y".
CREATE INDEX idx_rockets_agency_id_status
    ON rockets (agency_id, status);

-- ---------------------------------------------------------------------------
-- Comments
-- ---------------------------------------------------------------------------

COMMENT ON TABLE  rockets                    IS 'Individual launch vehicles belonging to a space agency.';
COMMENT ON COLUMN rockets.id                 IS 'Surrogate primary key.';
COMMENT ON COLUMN rockets.agency_id          IS 'FK to space_agencies. Never null — every rocket belongs to an agency.';
COMMENT ON COLUMN rockets.name               IS 'Rocket name (e.g. Falcon 9). Unique per agency.';
COMMENT ON COLUMN rockets.status             IS 'Lifecycle status: ACTIVE, RETIRED, or IN_DEVELOPMENT.';
COMMENT ON COLUMN rockets.height             IS 'Total height in metres.';
COMMENT ON COLUMN rockets.diameter           IS 'Maximum body diameter in metres.';
COMMENT ON COLUMN rockets.mass               IS 'Gross lift-off mass in kilograms.';
COMMENT ON COLUMN rockets.payload_to_leo     IS 'Maximum payload to low Earth orbit in kilograms.';
COMMENT ON COLUMN rockets.first_launch_date  IS 'Date of first successful launch. NULL if not yet launched.';
COMMENT ON COLUMN rockets.description        IS 'Plain-text description of the rocket.';
COMMENT ON COLUMN rockets.image_url          IS 'URL of a rocket image. Must begin with http:// or https://.';
COMMENT ON COLUMN rockets.created_at         IS 'Row creation timestamp (UTC).';
COMMENT ON COLUMN rockets.updated_at         IS 'Row last-update timestamp (UTC).';
