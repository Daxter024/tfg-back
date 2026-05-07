-- HU-TER-01: campos descriptivos del terreno (suelo, pendiente, riego, ref. catastral)
-- y rango de superficie permitido (0,01 ha = 100 m^2 a 10 000 ha = 1e8 m^2).

CREATE TYPE soil_type AS ENUM (
    'arcilloso',
    'franco',
    'arenoso',
    'calizo',
    'organico',
    'otro'
);

CREATE TYPE irrigation_type AS ENUM (
    'goteo',
    'aspersion',
    'gravedad',
    'secano'
);

ALTER TABLE terrain
    ADD COLUMN soil_type soil_type,
    ADD COLUMN slope_percent NUMERIC(5,2)
        CHECK (slope_percent IS NULL OR (slope_percent >= 0 AND slope_percent <= 100)),
    ADD COLUMN irrigation irrigation_type,
    ADD COLUMN cadastral_ref VARCHAR(40);

-- Rango de superficie aceptado (criterio HU-TER-01).
-- Constraint sobre columna generada: PostgreSQL permite chequearla porque
-- area_m2 ya esta materializada (STORED).
ALTER TABLE terrain
    ADD CONSTRAINT terrain_area_range CHECK (
        area_m2 BETWEEN 100 AND 100000000
    );

CREATE INDEX idx_terrain_cadastral_ref ON terrain(cadastral_ref);
