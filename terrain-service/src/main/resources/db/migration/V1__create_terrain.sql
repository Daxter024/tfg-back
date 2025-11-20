CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE terrain (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,

    geometry geometry(Polygon, 4326) NOT NULL,
    area_m2 NUMERIC(12,2),
    perimeter_m NUMERIC(12,2),
    centroid geometry(Point, 4326),

    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);