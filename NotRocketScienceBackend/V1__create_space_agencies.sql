-- =============================================================================
-- V1__create_space_agencies.sql
-- Creates the space_agencies table.
-- =============================================================================

CREATE TABLE space_agencies (
    id            BIGSERIAL       PRIMARY KEY,
    name          VARCHAR(100)    NOT NULL,
    country       VARCHAR(100)    NOT NULL,
    founded_year  INTEGER,
    description   TEXT,
    website       VARCHAR(255),
    logo_url      VARCHAR(500),
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT uq_space_agencies_name
        UNIQUE (name),

    CONSTRAINT chk_space_agencies_founded_year
        CHECK (founded_year IS NULL OR (founded_year >= 1900 AND founded_year <= EXTRACT(YEAR FROM NOW()))),

    CONSTRAINT chk_space_agencies_website
        CHECK (website IS NULL OR website ~* '^https?://'),

    CONSTRAINT chk_space_agencies_logo_url
        CHECK (logo_url IS NULL OR logo_url ~* '^https?://')
);

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------

-- Filtering agencies by country is an expected browse pattern.
CREATE INDEX idx_space_agencies_country
    ON space_agencies (country);

-- ---------------------------------------------------------------------------
-- Comments
-- ---------------------------------------------------------------------------

COMMENT ON TABLE  space_agencies                   IS 'Rocket launch agencies and space programs.';
COMMENT ON COLUMN space_agencies.id                IS 'Surrogate primary key.';
COMMENT ON COLUMN space_agencies.name              IS 'Full legal or common name of the agency. Must be unique.';
COMMENT ON COLUMN space_agencies.country           IS 'Country of origin or headquarters.';
COMMENT ON COLUMN space_agencies.founded_year      IS 'Year the agency was founded. NULL if unknown.';
COMMENT ON COLUMN space_agencies.description       IS 'Plain-text description of the agency mission and history.';
COMMENT ON COLUMN space_agencies.website           IS 'Official website URL. Must begin with http:// or https://.';
COMMENT ON COLUMN space_agencies.logo_url          IS 'URL of the agency logo image. Must begin with http:// or https://.';
COMMENT ON COLUMN space_agencies.created_at        IS 'Row creation timestamp (UTC).';
COMMENT ON COLUMN space_agencies.updated_at        IS 'Row last-update timestamp (UTC).';
