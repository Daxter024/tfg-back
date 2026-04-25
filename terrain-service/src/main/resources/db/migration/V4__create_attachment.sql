-- HU-TER-03: adjuntos documentales y visuales asociados a un terreno.
--
-- Storage: la columna `storage_key` guarda la ruta relativa al raíz del
-- volumen (decisión: filesystem local — opción A del paquete 02 §2.1).
-- En MinIO sería la object key, mismo esquema.

CREATE TABLE attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    terrain_id UUID NOT NULL REFERENCES terrain(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes <= 10 * 1024 * 1024),
    storage_key VARCHAR(512) NOT NULL,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attachment_terrain ON attachment(terrain_id);
