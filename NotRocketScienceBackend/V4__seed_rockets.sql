-- =============================================================================
-- V4__seed_rockets.sql
-- Seeds realistic rocket data for all five seeded agencies.
-- Depends on: V2__create_rockets.sql, V3__seed_space_agencies.sql
-- Uses a CTE to resolve agency names to IDs so the seed is not sensitive to
-- auto-increment values assigned by V3.
-- =============================================================================

WITH agency_ids AS (
    SELECT id, name FROM space_agencies
    WHERE name IN ('SpaceX', 'NASA', 'ISRO', 'Blue Origin', 'Rocket Lab')
)

INSERT INTO rockets (
    agency_id, name, status,
    height, diameter, mass, payload_to_leo,
    first_launch_date, description, image_url
)
VALUES

-- =============================================================================
-- SpaceX
-- =============================================================================
(
    (SELECT id FROM agency_ids WHERE name = 'SpaceX'),
    'Falcon 9',
    'ACTIVE',
    70.0, 3.7, 549054, 22800,
    '2010-06-04',
    'Falcon 9 is a two-stage, partially reusable medium-lift launch vehicle developed by SpaceX. '
    'The first stage uses nine Merlin engines burning RP-1 and liquid oxygen. It routinely lands '
    'back on a drone ship or landing zone for reflight, dramatically reducing launch costs. '
    'As of 2025 it is the world''s most frequently launched orbital rocket.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/CRS-7_(Falcon_9)_pre-launch_(19048463038).jpg/320px-CRS-7_(Falcon_9)_pre-launch_(19048463038).jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'SpaceX'),
    'Falcon Heavy',
    'ACTIVE',
    70.0, 12.2, 1420788, 63800,
    '2018-02-06',
    'Falcon Heavy is a heavy-lift launch vehicle derived from the Falcon 9 core, combining three '
    'Falcon 9 first-stage cores in parallel. With 27 Merlin engines at lift-off, it is one of the '
    'most powerful operational rockets in the world. Side boosters routinely land simultaneously '
    'for reuse. It is used for heavy government and commercial payloads.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Falcon_Heavy_demo_mission_%28color_corrected%29.jpg/320px-Falcon_Heavy_demo_mission_%28color_corrected%29.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'SpaceX'),
    'Starship',
    'IN_DEVELOPMENT',
    121.0, 9.0, 5000000, 150000,
    '2023-04-20',
    'Starship is a fully reusable, two-stage super-heavy-lift launch vehicle under development by '
    'SpaceX. The Super Heavy booster uses 33 Raptor engines running methane and liquid oxygen. '
    'The upper stage, also called Starship, carries crew or cargo. Designed for missions to Earth '
    'orbit, the Moon, and Mars, it is the largest and most powerful rocket ever built.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Starship_-_Integrated_Flight_Test_2_rollout_%2853241686868%29.jpg/320px-Starship_-_Integrated_Flight_Test_2_rollout_%2853241686868%29.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'SpaceX'),
    'Falcon 1',
    'RETIRED',
    22.25, 1.7, 38555, 670,
    '2006-03-24',
    'Falcon 1 was the first privately developed liquid-fuelled rocket to reach Earth orbit. '
    'SpaceX''s first launch vehicle, it demonstrated that a small private company could '
    'develop and operate an orbital rocket. After five flights the program was retired in favour '
    'of the larger Falcon 9.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Falcon1_flight4.jpg/320px-Falcon1_flight4.jpg'
),

-- =============================================================================
-- NASA
-- =============================================================================
(
    (SELECT id FROM agency_ids WHERE name = 'NASA'),
    'Space Launch System',
    'ACTIVE',
    111.25, 8.4, 2608000, 95000,
    '2022-11-16',
    'The Space Launch System (SLS) is NASA''s super-heavy-lift expendable launch vehicle, '
    'the primary vehicle for the Artemis lunar exploration program. The Block 1 configuration '
    'uses four RS-25 engines on the core stage supplemented by two solid rocket boosters. '
    'It is the most powerful rocket NASA has ever launched.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/Artemis_I_rollout_to_Launch_Pad_39B_%2802%29.jpg/320px-Artemis_I_rollout_to_Launch_Pad_39B_%2802%29.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'NASA'),
    'Space Shuttle',
    'RETIRED',
    56.1, 23.79, 2030000, 27500,
    '1981-04-12',
    'The Space Shuttle was a partially reusable low Earth orbit spacecraft system operated by '
    'NASA from 1981 to 2011. It comprised a winged orbiter, an external tank, and two solid '
    'rocket boosters. Over 135 missions delivered crew and cargo to orbit, constructed the '
    'International Space Station, and serviced the Hubble Space Telescope.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/9/9f/STS-116_space_shuttle_Discovery.jpg/320px-STS-116_space_shuttle_Discovery.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'NASA'),
    'Saturn V',
    'RETIRED',
    110.6, 10.1, 2970000, 130000,
    '1967-11-09',
    'Saturn V was a super-heavy-lift three-stage rocket developed for the Apollo program. '
    'Standing 110 metres tall and producing 34.5 MN of thrust at lift-off, it remains the '
    'most powerful rocket ever brought to operational status. It carried every Apollo mission '
    'to the Moon and launched Skylab. Thirteen were flown between 1967 and 1973.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Apollo_11_Saturn_V_lifting_off_on_July_16%2C_1969.jpg/320px-Apollo_11_Saturn_V_lifting_off_on_July_16%2C_1969.jpg'
),

-- =============================================================================
-- ISRO
-- =============================================================================
(
    (SELECT id FROM agency_ids WHERE name = 'ISRO'),
    'PSLV',
    'ACTIVE',
    44.0, 2.8, 295000, 3800,
    '1993-09-20',
    'The Polar Satellite Launch Vehicle is ISRO''s workhorse medium-lift rocket, renowned for '
    'its extraordinary reliability. Using alternating solid and liquid stages, it has deployed '
    'satellites to Sun-synchronous, geosynchronous transfer, and interplanetary orbits. '
    'PSLV lofted the Chandrayaan-1 and Mangalyaan probes and holds the world record for '
    'deploying 104 satellites in a single launch.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/PSLV-C37_launching_104_satellites.jpg/320px-PSLV-C37_launching_104_satellites.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'ISRO'),
    'GSLV Mk III',
    'ACTIVE',
    43.43, 4.0, 640000, 10000,
    '2017-06-05',
    'The Geosynchronous Satellite Launch Vehicle Mark III, also known as LVM3, is ISRO''s '
    'heavy-lift rocket and the most capable vehicle in the Indian space fleet. It carried '
    'the Chandrayaan-2 and Chandrayaan-3 missions. The LVM3 features two strap-on liquid '
    'boosters, a solid core stage, and a cryogenic upper stage burning liquid hydrogen '
    'and liquid oxygen.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/GSLV_Mk_III_D2_launch.jpg/320px-GSLV_Mk_III_D2_launch.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'ISRO'),
    'GSLV Mk II',
    'ACTIVE',
    49.13, 2.8, 414750, 5000,
    '2001-04-18',
    'The Geosynchronous Satellite Launch Vehicle Mark II is a medium-lift rocket designed '
    'to place Indian communication satellites into geosynchronous transfer orbit, reducing '
    'dependence on foreign launch providers. It employs an indigenous cryogenic upper stage '
    'developed entirely within India after technology transfer restrictions were imposed '
    'in the 1990s.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d5/GSLV-D5_launch.jpg/320px-GSLV-D5_launch.jpg'
),

-- =============================================================================
-- Blue Origin
-- =============================================================================
(
    (SELECT id FROM agency_ids WHERE name = 'Blue Origin'),
    'New Shepard',
    'ACTIVE',
    18.0, 3.66, NULL, NULL,
    '2015-04-29',
    'New Shepard is a vertical-take-off, vertical-landing sub-orbital rocket designed for '
    'space tourism and scientific research. The system consists of a reusable booster and a '
    'pressurised crew capsule that separates above the Kármán line, providing several minutes '
    'of weightlessness. Both booster and capsule land independently under parachutes and '
    'retro-thrust.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/6/6c/New_Shepard_booster_landing.jpg/320px-New_Shepard_booster_landing.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'Blue Origin'),
    'New Glenn',
    'ACTIVE',
    98.0, 7.0, 320000, 45000,
    '2025-01-16',
    'New Glenn is Blue Origin''s first orbital-class rocket, a two-stage heavy-lift vehicle '
    'powered by seven BE-4 engines burning liquefied natural gas and liquid oxygen on the '
    'first stage. The 7-metre fairing enables large commercial and government payloads. '
    'The first stage is designed for reuse, landing on a drone ship after stage separation.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/New_Glenn_on_pad_before_launch_%28NS-1%29.jpg/320px-New_Glenn_on_pad_before_launch_%28NS-1%29.jpg'
),

-- =============================================================================
-- Rocket Lab
-- =============================================================================
(
    (SELECT id FROM agency_ids WHERE name = 'Rocket Lab'),
    'Electron',
    'ACTIVE',
    18.0, 1.2, 13000, 300,
    '2017-05-25',
    'Electron is a two-stage, partially reusable small-lift launch vehicle designed for rapid '
    'and affordable dedicated small-satellite launches. Its nine Rutherford engines are the '
    'first electric-pump-fed rocket engines used in an orbital rocket, and are manufactured '
    'via additive manufacturing (3D printing). Rocket Lab recovers first stages using '
    'helicopter mid-air catch and ocean retrieval.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/1/16/Electron_rocket_on_pad_%22Still_Testing%22_mission.jpg/320px-Electron_rocket_on_pad_%22Still_Testing%22_mission.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'Rocket Lab'),
    'Neutron',
    'IN_DEVELOPMENT',
    40.0, 7.0, NULL, 13000,
    NULL,
    'Neutron is a medium-lift reusable rocket under development by Rocket Lab, targeting the '
    'small-to-medium constellation deployment market. The first stage is designed to land back '
    'at the launch site on four deployable legs. Neutron will use Archimedes engines burning '
    'liquid oxygen and methane and features a carbon-composite structure. First launch '
    'is targeted for the mid-2020s.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Neutron_rocket_render.jpg/320px-Neutron_rocket_render.jpg'
);
