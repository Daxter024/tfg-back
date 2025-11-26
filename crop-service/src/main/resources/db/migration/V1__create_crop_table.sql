CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE crop_type (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO crop_type (name) VALUES
('CEREAL'),
('FRUIT'),
('VEGETABLE'),
('TUBER'),
('LEGUME');

-- Es probable que UUID no sea necesario y con un SERIAL fuese suficiente
-- No se si puede haber problemas si hay muchas escrituras y entonces acaba perdiendo el SERIAL (no creo)
-- Depende del uso de la tabla, si es del usuario personal o es común para todos los usuarios

CREATE TABLE crop (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    description TEXT,
    crop_type_id INTEGER REFERENCES crop_type(id)
);

CREATE INDEX idx_crop_name ON crop (name);