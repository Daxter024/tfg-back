CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE terrain (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    geometry geometry(Polygon, 4326) NOT NULL,

    area_m2 NUMERIC(12,2) GENERATED ALWAYS AS(
        ST_Area(geometry::geography)
    ) STORED,

    perimeter_m NUMERIC(12,2) GENERATED ALWAYS AS(
        ST_Perimeter(geometry::geography)
    ) STORED,

    centroid geometry(Point, 4326) GENERATED ALWAYS AS (
        ST_Centroid(geometry)
    ) STORED,

    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE

    CONSTRAINT terrain_geom_valid CHECK (ST_IsValid(geometry)),
    CONSTRAINT terrain_geom_srid CHECK (ST_SRID(geometry) = 4326)
);

CREATE INDEX terrain_geom_gist_idx ON terrain USING GIST(geometry);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at
BEFORE UPDATE ON terrain
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();