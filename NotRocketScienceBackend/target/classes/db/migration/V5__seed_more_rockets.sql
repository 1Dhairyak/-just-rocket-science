-- =============================================================================
-- V5__seed_more_rockets.sql
-- Adds ESA, Roscosmos, CNSA, JAXA, ULA agencies and seeds all major rockets
-- for every agency including the existing ones (SpaceX, NASA, ISRO, etc.)
-- Depends on: V4__seed_rockets.sql
-- =============================================================================

-- =============================================================================
-- NEW AGENCIES
-- =============================================================================

INSERT INTO space_agencies (name, country, founded_year, description, website, logo_url)
VALUES
(
    'ESA',
    'Europe',
    1975,
    'The European Space Agency is an intergovernmental organisation of 22 member states dedicated '
    'to the exploration of space. Headquartered in Paris, ESA operates launch facilities at the '
    'Guiana Space Centre in Kourou, French Guiana. Its Ariane rocket family has been a global '
    'commercial launch leader for decades, and ESA contributes to the International Space Station '
    'and conducts scientific missions throughout the solar system.',
    'https://www.esa.int',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/ESA_logo_simple.svg/320px-ESA_logo_simple.svg.png'
),
(
    'Roscosmos',
    'Russia',
    1992,
    'Roscosmos is the governmental body responsible for the space science program of Russia and '
    'general aerospace research. Russia has one of the longest spaceflight histories in the world, '
    'launching the first satellite (Sputnik), first human (Yuri Gagarin), and first space station '
    '(Mir). Its Soyuz rocket family is the most frequently launched rocket in history. Roscosmos '
    'operates launch sites at Baikonur, Plesetsk, and the new Vostochny Cosmodrome.',
    'https://www.roscosmos.ru',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/ROSCOSMOS_logo_ru.svg/320px-ROSCOSMOS_logo_ru.svg.png'
),
(
    'CNSA',
    'China',
    1993,
    'The China National Space Administration is the national space agency of the People''s Republic '
    'of China, responsible for planning and development of space activities. China has rapidly '
    'expanded its space capabilities, operating the Long March rocket family, landing on the far '
    'side of the Moon with Chang''e 4, deploying the Tianhe space station, and landing the '
    'Tianwen-1 rover on Mars. China aims to land astronauts on the Moon before 2030.',
    'https://www.cnsa.gov.cn',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/China_National_Space_Administration_logo.svg/320px-China_National_Space_Administration_logo.svg.png'
),
(
    'JAXA',
    'Japan',
    2003,
    'The Japan Aerospace Exploration Agency is the Japanese national aerospace and space agency. '
    'JAXA was formed in 2003 through the merger of three predecessor organisations. It develops '
    'the H-IIA and H3 launch vehicles, conducts the Hayabusa asteroid sample-return missions, '
    'and operates the Japanese Experiment Module Kibo aboard the ISS. Japan has been a pioneer '
    'in asteroid exploration and solar observation.',
    'https://www.jaxa.jp',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/9/9f/JAXA_logo.svg/320px-JAXA_logo.svg.png'
),
(
    'ULA',
    'United States',
    2006,
    'United Launch Alliance is a joint venture between Boeing and Lockheed Martin, formed to '
    'provide reliable launch services to the United States government. ULA operates the Atlas V '
    'and Delta IV Heavy rockets, and is developing the next-generation Vulcan Centaur. ULA has '
    'an exceptional record of mission success, having launched over 150 consecutive successful '
    'missions including national security, NASA science, and commercial payloads.',
    'https://www.ulalaunch.com',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/ULA_logo.svg/320px-ULA_logo.svg.png'
)
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- ADDITIONAL ROCKETS FOR EXISTING AGENCIES
-- =============================================================================

WITH agency_ids AS (
    SELECT id, name FROM space_agencies
)

INSERT INTO rockets (
    agency_id, name, status,
    height, diameter, mass, payload_to_leo,
    first_launch_date, description, image_url
)
VALUES

-- -----------------------------------------------------------------------
-- SpaceX (additional)
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'SpaceX'),
    'Dragon',
    'ACTIVE',
    8.1, 4.0, 12055, NULL,
    '2010-12-08',
    'Dragon is a partially reusable spacecraft developed by SpaceX to transport crew and cargo '
    'to the International Space Station under NASA''s Commercial Crew and Cargo programs. '
    'The pressurised section can carry up to seven astronauts. The trunk section carries '
    'unpressurised cargo and solar panels. Dragon is the first commercial spacecraft to '
    'return cargo from the ISS and to carry crew.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Crew_Dragon_Endeavour_approaches_the_ISS_%28cropped%29.jpg/320px-Crew_Dragon_Endeavour_approaches_the_ISS_%28cropped%29.jpg'
),

-- -----------------------------------------------------------------------
-- NASA (additional)
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'NASA'),
    'Atlas-Centaur',
    'RETIRED',
    37.2, 3.05, 147418, 8618,
    '1962-05-08',
    'The Atlas-Centaur was a family of American expendable launch vehicles derived from the '
    'Atlas ICBM, combined with the Centaur cryogenic upper stage. It launched the Surveyor '
    'Moon landers, Pioneer 10 and 11, and numerous communication satellites. The Centaur '
    'upper stage, burning liquid hydrogen and liquid oxygen, pioneered high-energy upper '
    'stage technology.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'NASA'),
    'Titan IV',
    'RETIRED',
    62.2, 3.05, 943000, 21680,
    '1989-06-14',
    'The Titan IV was the largest and most capable expendable launch vehicle operated by the '
    'United States Air Force. Used extensively to launch classified national security payloads '
    'and large NASA missions including the Cassini–Huygens Saturn probe. It flew 39 times '
    'between 1989 and 2005 before being retired in favour of the Delta IV and Atlas V.',
    NULL
),

-- -----------------------------------------------------------------------
-- ISRO (additional)
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'ISRO'),
    'SLV',
    'RETIRED',
    22.0, 1.0, 17000, 40,
    '1979-08-10',
    'The Satellite Launch Vehicle was India''s first indigenous orbital rocket, a four-stage '
    'all-solid rocket developed by ISRO under the leadership of Dr. A.P.J. Abdul Kalam. '
    'It successfully placed the Rohini satellite into orbit in 1980, making India the sixth '
    'country to achieve indigenous satellite launch capability. Four flights were conducted '
    'between 1979 and 1983.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'ISRO'),
    'ASLV',
    'RETIRED',
    23.5, 1.0, 40600, 150,
    '1987-03-24',
    'The Augmented Satellite Launch Vehicle was an intermediate rocket developed by ISRO to '
    'enhance payload capacity over the SLV. It added two strap-on solid boosters to the SLV '
    'core. The program experienced early failures before achieving two successful flights. '
    'Its technology contributed directly to the development of PSLV strap-on boosters.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'ISRO'),
    'LVM3',
    'ACTIVE',
    43.5, 4.0, 640000, 10000,
    '2017-06-05',
    'Launch Vehicle Mark-3 (LVM3), formerly GSLV Mk III, is ISRO''s most powerful rocket. '
    'It features two liquid strap-on boosters, a solid core stage, and an indigenous cryogenic '
    'upper stage. LVM3 successfully carried the Chandrayaan-3 mission to the Moon in 2023. '
    'It is also contracted to launch OneWeb broadband satellites and is ISRO''s primary '
    'vehicle for heavy payloads.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/LVM3-M1_OneWeb_India-1_mission.jpg/320px-LVM3-M1_OneWeb_India-1_mission.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'ISRO'),
    'SSLV',
    'ACTIVE',
    34.0, 2.0, 120000, 500,
    '2022-08-07',
    'The Small Satellite Launch Vehicle is ISRO''s newest rocket, designed for rapid, '
    'low-cost launches of small satellites. It uses three solid propulsion stages and a '
    'velocity trimming module for precise orbit insertion. SSLV can be assembled by a '
    'small team in days rather than months, targeting the booming small satellite market '
    'and enabling on-demand launch services.',
    NULL
),

-- -----------------------------------------------------------------------
-- Blue Origin (additional)
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'Blue Origin'),
    'BE-4 Engine',
    'ACTIVE',
    NULL, NULL, NULL, NULL,
    '2022-09-01',
    'The BE-4 is a rocket engine developed by Blue Origin burning liquefied natural gas and '
    'liquid oxygen with 2.4 MN of thrust. It powers both Blue Origin''s own New Glenn rocket '
    'and United Launch Alliance''s Vulcan Centaur. The BE-4 uses an oxidiser-rich staged '
    'combustion cycle and is designed for reuse. Its development was a major milestone in '
    'American rocket engine manufacturing.',
    NULL
),

-- -----------------------------------------------------------------------
-- Rocket Lab (additional)
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'Rocket Lab'),
    'Photon',
    'ACTIVE',
    NULL, NULL, NULL, NULL,
    '2020-08-31',
    'Photon is Rocket Lab''s spacecraft platform derived from the Electron upper stage. '
    'It provides power, propulsion, attitude control, and communications for satellite '
    'missions. Photon has been used for Earth observation, interplanetary missions including '
    'the CAPSTONE lunar pathfinder mission for NASA, and as a platform for commercial '
    'small satellite operators needing a complete spacecraft solution.',
    NULL
),

-- -----------------------------------------------------------------------
-- ESA
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'ESA'),
    'Ariane 5',
    'RETIRED',
    53.0, 5.4, 777000, 21000,
    '1996-06-04',
    'Ariane 5 was ESA''s heavy-lift expendable launch vehicle, the workhorse of European '
    'commercial launch services for nearly three decades. It successfully deployed the James '
    'Webb Space Telescope, dual commercial communication satellites, and dozens of '
    'interplanetary probes. The Vulcain 2 cryogenic core engine and twin solid boosters '
    'delivered unmatched reliability — 82 of 117 consecutive successes. Retired in 2023.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/6/62/Ariane_5_ES_launching_ATV_Johannes_Kepler.jpg/320px-Ariane_5_ES_launching_ATV_Johannes_Kepler.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'ESA'),
    'Ariane 6',
    'ACTIVE',
    62.0, 5.4, 530000, 21650,
    '2024-07-09',
    'Ariane 6 is ESA''s next-generation heavy-lift launch vehicle, succeeding Ariane 5. '
    'Available in two configurations: A62 with two solid strap-on boosters and A64 with four. '
    'The Vinci upper stage engine is restartable multiple times, enabling complex multi-orbit '
    'missions and deorbit burns to reduce space debris. First launch was achieved in July 2024.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/3/3f/Ariane_6_first_launch.jpg/320px-Ariane_6_first_launch.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'ESA'),
    'Vega',
    'ACTIVE',
    30.0, 3.03, 137000, 1500,
    '2012-02-13',
    'Vega is ESA''s small-lift launch vehicle, developed primarily by Italy''s Avio. '
    'It uses three solid stages and a liquid upper stage (AVUM) for precise orbit injection. '
    'Vega is designed to launch small scientific satellites, Earth observation payloads, '
    'and technology demonstrators to polar and sun-synchronous orbits from the Guiana '
    'Space Centre.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Vega_VV01_launch_campaign.jpg/320px-Vega_VV01_launch_campaign.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'ESA'),
    'Vega-C',
    'ACTIVE',
    35.0, 3.4, 210000, 2350,
    '2022-07-13',
    'Vega-C is the enhanced successor to Vega, offering increased payload capacity and '
    'a larger fairing. It introduces the P120C solid motor as its first stage — the same '
    'motor used as a strap-on booster on Ariane 6, achieving economies of scale. '
    'Vega-C restores European autonomous access to space for small satellites following '
    'return to flight after a 2022 mission failure.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'ESA'),
    'Ariane 1',
    'RETIRED',
    47.4, 3.8, 210000, 1850,
    '1979-12-24',
    'Ariane 1 was the first rocket in the Ariane family, developed by ESA to provide Europe '
    'with independent access to space. Its first flight on 24 December 1979 marked the start '
    'of a European launch industry that would become a global leader. Ariane 1 used a '
    'Viking first stage and delivered communication satellites to geostationary transfer orbit.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'ESA'),
    'Ariane 4',
    'RETIRED',
    58.72, 3.8, 470000, 10200,
    '1988-06-15',
    'Ariane 4 was ESA''s highly successful workhorse rocket of the 1990s, available in six '
    'configurations with different combinations of liquid and solid strap-on boosters. '
    'It captured over 50% of the global commercial satellite launch market at its peak. '
    'The rocket flew 116 times between 1988 and 2003 with 97 complete successes.',
    NULL
),

-- -----------------------------------------------------------------------
-- Roscosmos
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'Roscosmos'),
    'Soyuz',
    'ACTIVE',
    46.3, 2.95, 308000, 7020,
    '1966-11-28',
    'The Soyuz is a family of Soviet and Russian expendable launch vehicles that has been '
    'in continuous service since 1966, making it the most frequently launched rocket in '
    'history with over 1,900 flights. It carries crew to the International Space Station, '
    'launches satellites, and deploys Galileo and OneWeb constellation satellites. '
    'The Soyuz spacecraft derived from it remains the sole crew transport to the ISS for '
    'a decade after the Space Shuttle retirement.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d6/Soyuz_MS-17_launch.jpg/320px-Soyuz_MS-17_launch.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'Roscosmos'),
    'Proton',
    'ACTIVE',
    58.2, 7.4, 705000, 23000,
    '1965-07-16',
    'The Proton rocket family is Russia''s workhorse heavy-lift launch vehicle, operated '
    'since 1965. Proton-M, the current variant, uses six first-stage RD-275M engines '
    'burning hypergolic propellants. It has launched the Mir space station modules, '
    'Zarya and Zvezda ISS modules, and hundreds of commercial geostationary satellites. '
    'Despite high capability, its propellant is highly toxic.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Proton_Zvezda_launch.jpg/320px-Proton_Zvezda_launch.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'Roscosmos'),
    'Angara A5',
    'ACTIVE',
    64.0, 8.86, 773000, 24500,
    '2014-12-23',
    'Angara A5 is Russia''s new heavy-lift modular rocket, developed to replace the Proton. '
    'It uses non-toxic liquid oxygen and kerosene propellants — a major improvement over '
    'Proton''s toxic UDMH. The modular Universal Rocket Module design allows scaling from '
    'small to heavy-lift variants. Angara is intended to be launched from Russian territory '
    'rather than the Baikonur Cosmodrome in Kazakhstan.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0d/Angara_A5_first_launch.jpg/320px-Angara_A5_first_launch.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'Roscosmos'),
    'Soyuz-2',
    'ACTIVE',
    46.3, 2.95, 312000, 8200,
    '2004-11-08',
    'Soyuz-2 is the modernised version of the classic Soyuz rocket with a digital flight '
    'control system, upgraded NK-33 engines on the first and second stages, and improved '
    'third stage. It can launch more payload than the original Soyuz and has been used '
    'for Galileo navigation satellites, OneWeb broadband constellation, and the Meteor-M '
    'meteorological satellite series.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'Roscosmos'),
    'Rokot',
    'RETIRED',
    29.15, 2.5, 107500, 1950,
    '1990-11-20',
    'Rokot is a Russian small-lift launch vehicle derived from the UR-100N intercontinental '
    'ballistic missile. It uses a Briz-KM upper stage and launches from Plesetsk Cosmodrome. '
    'Rokot has deployed European Space Agency''s GOCE, SWARM, and Cryosat Earth observation '
    'satellites. The program is being retired due to its reliance on Ukrainian components.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'Roscosmos'),
    'Zenit',
    'RETIRED',
    57.0, 3.9, 459000, 13740,
    '1985-04-13',
    'Zenit was a Soviet and Ukrainian medium-to-heavy-lift rocket used by both the Soviet '
    'space program and commercial Sea Launch consortium. Notable for its highly automated '
    'launch procedures. The Zenit-3SL Sea Launch variant launched from a converted oil '
    'platform on the equator for maximum geostationary payload performance. Production '
    'ceased following the Ukraine conflict.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'Roscosmos'),
    'N1',
    'RETIRED',
    105.0, 17.0, 2735000, 90000,
    '1969-02-21',
    'The N1 was the Soviet Union''s super-heavy-lift rocket intended to compete with '
    'the American Saturn V for a crewed lunar landing. All four launch attempts between '
    '1969 and 1972 ended in failure, with the second launch causing one of the largest '
    'non-nuclear explosions in history. The program was cancelled in 1976 and kept secret '
    'until 1989.',
    NULL
),

-- -----------------------------------------------------------------------
-- CNSA
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'CNSA'),
    'Long March 2',
    'ACTIVE',
    43.25, 3.35, 260000, 8500,
    '1974-11-05',
    'Long March 2 is a family of medium-lift Chinese launch vehicles derived from the '
    'DF-5 intercontinental ballistic missile. The CZ-2C and CZ-2D variants remain in '
    'active service for satellite launches. Long March 2F is the crew-rated variant '
    'that carries Shenzhou crewed spacecraft and Tianzhou cargo vessels to the Chinese '
    'Space Station Tiangong.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'CNSA'),
    'Long March 3',
    'ACTIVE',
    54.84, 3.35, 345000, 12000,
    '1984-01-29',
    'Long March 3 is a medium-to-heavy-lift rocket family with a cryogenic hydrogen-oxygen '
    'third stage, enabling high-energy geostationary transfer orbit missions. It has launched '
    'China''s Beidou navigation constellation, Chang''e lunar probes, and commercial '
    'communication satellites. The Long March 3B enhanced variant is the primary Chinese '
    'vehicle for geostationary payloads.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'CNSA'),
    'Long March 5',
    'ACTIVE',
    56.97, 5.0, 879000, 25000,
    '2016-11-03',
    'Long March 5 is China''s heavy-lift rocket and the backbone of its deep-space ambitions. '
    'Using liquid oxygen and liquid hydrogen on the core stage with kerosene strap-on boosters, '
    'it launched the Tianwen-1 Mars mission, Chang''e-5 lunar sample return, and the Tianhe '
    'core module of the Chinese Space Station. Long March 5B, a variant without an upper stage, '
    'delivers large payloads to low Earth orbit.',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/5/54/Long_March_5B_launch_with_Wentian_module.jpg/320px-Long_March_5B_launch_with_Wentian_module.jpg'
),
(
    (SELECT id FROM agency_ids WHERE name = 'CNSA'),
    'Long March 7',
    'ACTIVE',
    53.1, 3.35, 594000, 13500,
    '2016-06-25',
    'Long March 7 is a medium-lift rocket designed to launch the Tianzhou cargo spacecraft '
    'to the Chinese Space Station and to serve as a next-generation replacement for Long March 2F. '
    'It uses liquid oxygen and kerosene — cleaner propellants than the toxic UDMH used by '
    'older Long March variants. Long March 7A adds a cryogenic upper stage for geostationary '
    'missions.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'CNSA'),
    'Long March 9',
    'IN_DEVELOPMENT',
    111.0, 10.6, 4140000, 150000,
    NULL,
    'Long March 9 is China''s planned super-heavy-lift rocket intended for crewed lunar '
    'missions and large space infrastructure deployment. With a target payload of 150 tonnes '
    'to LEO, it is comparable to NASA''s SLS and SpaceX''s Starship. China plans to use it '
    'for a crewed Moon landing before 2030 and eventual crewed Mars missions. First launch '
    'is targeted around 2030.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'CNSA'),
    'Kuaizhou-1A',
    'ACTIVE',
    20.0, 1.4, 30000, 300,
    '2017-01-09',
    'Kuaizhou-1A is a quick-reaction small-lift solid-fuelled rocket operated by ExPace, '
    'a commercial subsidiary of the Chinese state aerospace company CASIC. It can be '
    'prepared and launched within 24 hours, making it useful for rapid-response satellite '
    'deployment. It targets the growing small satellite market and has launched commercial '
    'Earth observation and IoT constellation satellites.',
    NULL
),

-- -----------------------------------------------------------------------
-- JAXA
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'JAXA'),
    'H-IIA',
    'ACTIVE',
    53.0, 4.0, 289000, 10000,
    '2001-08-29',
    'The H-IIA is Japan''s primary medium-to-heavy-lift launch vehicle, developed by '
    'Mitsubishi Heavy Industries under JAXA oversight. It uses cryogenic liquid hydrogen '
    'and liquid oxygen propellants with optional solid strap-on boosters. H-IIA has launched '
    'the Hayabusa2 asteroid sample-return mission, Akatsuki Venus orbiter, and intelligence '
    'satellites. It has achieved 48 successes in 49 flights.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'JAXA'),
    'H3',
    'ACTIVE',
    57.0, 5.2, 574000, 16500,
    '2024-02-17',
    'H3 is JAXA''s next-generation launch vehicle, developed to replace the H-IIA at lower '
    'cost and with greater flexibility. It uses the new LE-9 cryogenic engine, with two or '
    'three core engines and optional solid strap-on boosters in multiple configurations. '
    'After a failed first test flight in 2023, H3 achieved its first successful launch in '
    'February 2024 with the ALOS-4 Earth observation satellite.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'JAXA'),
    'H-IIB',
    'RETIRED',
    56.6, 5.2, 531000, 19000,
    '2009-09-10',
    'The H-IIB was a heavy-lift variant of the H-IIA developed specifically to launch the '
    'HTV (Kounotori) cargo spacecraft to the International Space Station. It used two LE-7A '
    'engines on the core stage and a 5.1-metre fairing to accommodate the large HTV. '
    'All nine H-IIB flights were successful, delivering approximately 6 tonnes of supplies '
    'per mission to the ISS. Retired in 2020.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'JAXA'),
    'Epsilon',
    'ACTIVE',
    26.0, 2.6, 95000, 1500,
    '2013-09-14',
    'Epsilon is JAXA''s solid-fuelled small-lift rocket, designed for affordable and rapid '
    'launches of scientific satellites. It uses mobile launch equipment and an advanced '
    'autonomous health monitoring system, allowing it to be operated with a small team. '
    'Epsilon carries JAXA science missions including the ERG (Arase) space environment '
    'satellite and the RAISING-3 small satellite.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'JAXA'),
    'N-I',
    'RETIRED',
    32.6, 2.44, 90500, 1200,
    '1975-09-09',
    'N-I was Japan''s first domestically assembled orbital launch vehicle, built with '
    'significant technology transfer from the United States under McDonnell Douglas. '
    'It was based on the Thor-Delta design and used a US-provided Thiokol solid motor '
    'for attitude control. N-I flew seven times between 1975 and 1982, launching '
    'Japanese communication and meteorological satellites.',
    NULL
),

-- -----------------------------------------------------------------------
-- ULA
-- -----------------------------------------------------------------------
(
    (SELECT id FROM agency_ids WHERE name = 'ULA'),
    'Atlas V',
    'ACTIVE',
    58.3, 3.81, 590000, 18810,
    '2002-08-21',
    'Atlas V is ULA''s reliable medium-to-heavy-lift rocket, available in configurations '
    'with zero to five solid strap-on boosters and two fairing sizes. Powered by the '
    'Russian RD-180 engine on the first stage and RL-10 on the Centaur upper stage. '
    'It launched New Horizons to Pluto, Curiosity and Perseverance Mars rovers, Juno '
    'to Jupiter, and dozens of national security payloads.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'ULA'),
    'Delta IV Heavy',
    'ACTIVE',
    72.0, 15.0, 733000, 28790,
    '2004-12-21',
    'Delta IV Heavy is America''s most powerful operational rocket alongside SLS. It uses '
    'three Common Booster Cores each powered by an RS-68A engine burning cryogenic hydrogen '
    'and oxygen. Reserved for the largest national security payloads — spy satellites, GPS '
    'birds, and NRO missions — that require maximum performance. Being phased out in favour '
    'of the Vulcan Centaur.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'ULA'),
    'Vulcan Centaur',
    'ACTIVE',
    61.6, 5.4, 546700, 27200,
    '2024-01-08',
    'Vulcan Centaur is ULA''s next-generation heavy-lift rocket, replacing both Atlas V and '
    'Delta IV. It uses Blue Origin BE-4 engines burning liquefied natural gas and liquid '
    'oxygen, eliminating the dependence on Russian RD-180 engines. The Centaur V upper '
    'stage uses two RL-10 engines. Vulcan Centaur achieved its first successful launch in '
    'January 2024, carrying the Peregrine lunar lander.',
    NULL
),
(
    (SELECT id FROM agency_ids WHERE name = 'ULA'),
    'Delta II',
    'RETIRED',
    38.2, 2.44, 231870, 6100,
    '1989-02-14',
    'Delta II was one of the most reliable rockets in spaceflight history, achieving 155 '
    'consecutive successes at one point. It launched Mars Pathfinder, the Spirit and '
    'Opportunity Mars rovers, the Spitzer Space Telescope, the Dawn asteroid mission, '
    'and the original GPS Block II constellation satellites. After 155 total flights, '
    'the final Delta II launched in 2018.',
    NULL
)
ON CONFLICT (agency_id, name) DO NOTHING;
