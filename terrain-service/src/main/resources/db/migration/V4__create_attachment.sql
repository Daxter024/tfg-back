-- HU-TER-03: tabla de adjuntos documentales / visuales asociados a un terreno.
-- Almacenamiento por defecto: volumen local (clave en columna storage_key).
-- La cuota acumulada por terreno (100 MB) se valida en service, no aqui.

CREATE TABLE attachment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    terrain_id    UUID NOT NULL REFERENCES terrain(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10 * 1024 * 1024),
    storage_key   VARCHAR(512) NOT NULL,
    uploaded_by   UUID NOT NULL,
    uploaded_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attachment_terrain ON attachment(terrain_id);
