-- V7__seed_first_launch_dates.sql
-- Populates first_launch_date for all seeded rockets.
-- Long March 9 remains NULL (still In Development, no confirmed launch date).

-- ── NASA ────────────────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '1962-05-08' WHERE name = 'Atlas-Centaur';
UPDATE rockets SET first_launch_date = '1967-11-09' WHERE name = 'Saturn V';
UPDATE rockets SET first_launch_date = '2022-11-16' WHERE name = 'SLS';
UPDATE rockets SET first_launch_date = '1989-06-14' WHERE name = 'Titan IV';

-- ── SpaceX ───────────────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '2010-12-08' WHERE name = 'Dragon';
UPDATE rockets SET first_launch_date = '2010-06-04' WHERE name = 'Falcon 9';
UPDATE rockets SET first_launch_date = '2018-02-06' WHERE name = 'Falcon Heavy';
UPDATE rockets SET first_launch_date = '2023-04-20' WHERE name = 'Starship';

-- ── ISRO ─────────────────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '1987-03-24' WHERE name = 'ASLV';
UPDATE rockets SET first_launch_date = '2001-04-18' WHERE name = 'GSLV Mk II';
UPDATE rockets SET first_launch_date = '2014-12-18' WHERE name = 'LVM3';
UPDATE rockets SET first_launch_date = '1993-09-20' WHERE name = 'PSLV';
UPDATE rockets SET first_launch_date = '1979-08-10' WHERE name = 'SLV-3';
UPDATE rockets SET first_launch_date = '2022-08-07' WHERE name = 'SSLV';

-- ── ESA / Arianespace ────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '1979-12-24' WHERE name = 'Ariane 1';
UPDATE rockets SET first_launch_date = '1988-06-15' WHERE name = 'Ariane 4';
UPDATE rockets SET first_launch_date = '1996-06-04' WHERE name = 'Ariane 5';
UPDATE rockets SET first_launch_date = '2024-07-09' WHERE name = 'Ariane 6';
UPDATE rockets SET first_launch_date = '2012-02-13' WHERE name = 'Vega';
UPDATE rockets SET first_launch_date = '2022-07-13' WHERE name = 'Vega-C';

-- ── Roscosmos ────────────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '2014-07-09' WHERE name = 'Angara 1.2';
UPDATE rockets SET first_launch_date = '2014-12-23' WHERE name = 'Angara A5';
UPDATE rockets SET first_launch_date = '1969-02-21' WHERE name = 'N1-L3';
UPDATE rockets SET first_launch_date = '2001-04-07' WHERE name = 'Proton-M';
UPDATE rockets SET first_launch_date = '1990-11-20' WHERE name = 'Rokot';
UPDATE rockets SET first_launch_date = '1966-11-28' WHERE name = 'Soyuz';
UPDATE rockets SET first_launch_date = '2004-11-08' WHERE name = 'Soyuz-2';
UPDATE rockets SET first_launch_date = '1985-04-13' WHERE name = 'Zenit-2';

-- ── CASC (China) ─────────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '2015-09-25' WHERE name = 'Long March 11';
UPDATE rockets SET first_launch_date = '2024-11-30' WHERE name = 'Long March 12';
UPDATE rockets SET first_launch_date = '1974-11-05' WHERE name = 'Long March 2';
UPDATE rockets SET first_launch_date = '1999-11-20' WHERE name = 'Long March 2F';
UPDATE rockets SET first_launch_date = '1984-01-29' WHERE name = 'Long March 3';
UPDATE rockets SET first_launch_date = '1996-02-15' WHERE name = 'Long March 3B';
UPDATE rockets SET first_launch_date = '2016-11-03' WHERE name = 'Long March 5';
UPDATE rockets SET first_launch_date = '2020-05-05' WHERE name = 'Long March 5B';
UPDATE rockets SET first_launch_date = '2016-06-25' WHERE name = 'Long March 7';
UPDATE rockets SET first_launch_date = NULL          WHERE name = 'Long March 9';  -- In Development

-- ── JAXA ─────────────────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '2013-09-14' WHERE name = 'Epsilon';
UPDATE rockets SET first_launch_date = '2001-08-29' WHERE name = 'H-IIA';
UPDATE rockets SET first_launch_date = '2009-09-11' WHERE name = 'H-IIB';
UPDATE rockets SET first_launch_date = '2023-03-07' WHERE name = 'H3';
UPDATE rockets SET first_launch_date = '1975-09-09' WHERE name = 'N-I';

-- ── Blue Origin ───────────────────────────────────────────────────────────────
UPDATE rockets SET first_launch_date = '2025-01-16' WHERE name = 'New Glenn';
