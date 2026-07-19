-- =============================================================================
-- V3__seed_space_agencies.sql
-- Seeds five real space agencies.
-- Depends on: V1__create_space_agencies.sql
-- =============================================================================

INSERT INTO space_agencies (name, country, founded_year, description, website, logo_url)
VALUES
(
    'SpaceX',
    'United States',
    2002,
    'Space Exploration Technologies Corp. is a private American aerospace manufacturer and space '
    'transportation company founded by Elon Musk. SpaceX designs, manufactures, and launches '
    'advanced rockets and spacecraft. The company has pioneered reusable rocket technology with '
    'its Falcon 9 and Falcon Heavy vehicles, and is developing the fully reusable Starship system '
    'for missions to the Moon, Mars, and beyond.',
    'https://www.spacex.com',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/SpaceX-Logo-Xonly.svg/320px-SpaceX-Logo-Xonly.svg.png'
),
(
    'NASA',
    'United States',
    1958,
    'The National Aeronautics and Space Administration is the United States government agency '
    'responsible for the civilian space program as well as aeronautics and space research. NASA '
    'was established by the National Aeronautics and Space Act on July 29, 1958. It has led '
    'landmark programs including Apollo, the Space Shuttle, the International Space Station, '
    'and the Artemis program targeting a return to the Moon.',
    'https://www.nasa.gov',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/NASA_logo.svg/320px-NASA_logo.svg.png'
),
(
    'ISRO',
    'India',
    1969,
    'The Indian Space Research Organisation is the national space agency of India, headquartered '
    'in Bengaluru. Founded in 1969, ISRO has developed the Polar Satellite Launch Vehicle (PSLV) '
    'and Geosynchronous Satellite Launch Vehicle (GSLV) families, and demonstrated cost-effective '
    'interplanetary capability with Mangalyaan (Mars Orbiter Mission) and the Chandrayaan '
    'lunar missions.',
    'https://www.isro.gov.in',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b4/ISRO_Logo.svg/320px-ISRO_Logo.svg.png'
),
(
    'Blue Origin',
    'United States',
    2000,
    'Blue Origin, LLC is a private American aerospace manufacturer and sub-orbital spaceflight '
    'services company founded by Jeff Bezos. Blue Origin manufactures launch vehicles and '
    'rocket engines, with a stated goal of making space access more affordable and reliable. '
    'Its New Shepard vehicle has completed numerous human spaceflight missions, and the New Glenn '
    'orbital rocket began launches in 2025.',
    'https://www.blueorigin.com',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Blue_Origin_logo.svg/320px-Blue_Origin_logo.svg.png'
),
(
    'Rocket Lab',
    'United States',
    2006,
    'Rocket Lab USA, Inc. is an American public aerospace manufacturer and launch service provider '
    'with a New Zealand heritage. The company specialises in small satellite launch via its Electron '
    'vehicle, which features a carbon-composite structure and the 3D-printed Rutherford engine. '
    'Rocket Lab is also developing the mid-class Neutron rocket to serve larger constellation '
    'and government payload markets.',
    'https://www.rocketlabusa.com',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/Rocket_Lab_Logo.svg/320px-Rocket_Lab_Logo.svg.png'
);
