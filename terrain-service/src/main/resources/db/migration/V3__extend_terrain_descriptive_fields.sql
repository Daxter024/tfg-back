-- HU-TER-01: extender el terreno con campos descriptivos (tipo de suelo,
-- pendiente, sistema de riego, referencia catastral) y rango de área.
--
-- Nota: PostgreSQL no permite añadir un CHECK sobre una columna generada
-- si alguna fila existente lo viola. En entornos con datos previos se
-- debe migrar/excluir antes de aplicar.

CREATE TYPE soil_type AS ENUM (
    'arcilloso','franco','arenoso','calizo','organico','otro'
);

CREATE TYPE irrigation_type AS ENUM (
    'goteo','aspersion','gravedad','secano'
);

ALTER TABLE terrain
    ADD COLUMN soil_type     soil_type,
    ADD COLUMN slope_percent NUMERIC(5,2)
        CHECK (slope_percent IS NULL OR (slope_percent >= 0 AND slope_percent <= 100)),
    ADD COLUMN irrigation    irrigation_type,
    ADD COLUMN cadastral_ref VARCHAR(40),
    ADD CONSTRAINT terrain_area_range CHECK (
        area_m2 BETWEEN 100 AND 100000000
    );

CREATE INDEX idx_terrain_cadastral_ref ON terrain(cadastral_ref);
