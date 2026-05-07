-- HU-TER-04: division del terreno en parcelas (sub-poligonos disjuntos).
-- Cada parcela vive dentro de un terreno padre y NO se solapa con otras del
-- mismo padre. La validacion ST_Within / ST_Overlaps se hace en service.

CREATE TABLE parcel (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    terrain_id  UUID NOT NULL REFERENCES terrain(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    geometry    geometry(Polygon, 4326) NOT NULL,

    area_m2     NUMERIC(12,2) GENERATED ALWAYS AS (
        ST_Area(geometry::geography)
    ) STORED,

    perimeter_m NUMERIC(12,2) GENERATED ALWAYS AS (
        ST_Perimeter(geometry::geography)
    ) STORED,

    centroid    geometry(Point, 4326) GENERATED ALWAYS AS (
        ST_Centroid(geometry)
    ) STORED,

    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT parcel_geom_valid CHECK (ST_IsValid(geometry)),
    CONSTRAINT parcel_geom_srid  CHECK (ST_SRID(geometry) = 4326),
    CONSTRAINT parcel_name_unique_per_terrain UNIQUE (terrain_id, name)
);

CREATE INDEX parcel_geom_gist_idx ON parcel USING GIST(geometry);
CREATE INDEX idx_parcel_terrain ON parcel(terrain_id);

-- Trigger que mantiene parcel.updated_at al ritmo de los UPDATE.
-- Reusamos la funcion update_updated_at() declarada por V1__create_terrain.sql.
CREATE TRIGGER set_parcel_updated_at
    BEFORE UPDATE ON parcel
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();
