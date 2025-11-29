CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE season_type (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);

INSERT INTO season_type (name) VALUES
('Planting'),
('Harvest'),
('Fallow'),
('Dormancy');

CREATE table season (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_terrain UUID NOT NULL,
    id_crop UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    season_type_id INTEGER REFERENCES season_type(id),
    observations TEXT,

    CONSTRAINT start_before_end CHECK ( end_date IS NULL OR end_date >= start_date )
);