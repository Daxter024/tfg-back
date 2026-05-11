CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- input — catalogo "por usuario" con dimension stock calculada de movimientos
-- ---------------------------------------------------------------------------
CREATE TYPE input_category AS ENUM (
  'fertilizante','fitosanitario','semilla','agua','combustible','otro'
);

CREATE TABLE input (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(200) NOT NULL,
  category input_category NOT NULL,
  unit VARCHAR(16) NOT NULL,
  low_stock_threshold NUMERIC(14,3),
  supplier VARCHAR(200),
  notes TEXT,
  created_by UUID NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP,
  CONSTRAINT input_name_unique_per_category_alive
    UNIQUE (name, category, deleted_at)
);
CREATE INDEX idx_input_category   ON input(category) WHERE deleted_at IS NULL;
CREATE INDEX idx_input_created_by ON input(created_by);
CREATE INDEX idx_input_name_trgm  ON input USING gin (name gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- input_movement — log inmutable de entradas/salidas; el stock se calcula
-- sumando algebraicamente IN(+) y OUT(-) sobre esta tabla.
-- ---------------------------------------------------------------------------
CREATE TYPE movement_kind AS ENUM ('IN','OUT');

CREATE TABLE input_movement (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  input_id UUID NOT NULL REFERENCES input(id) ON DELETE RESTRICT,
  kind movement_kind NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
  occurred_at DATE NOT NULL,
  task_id UUID,
  performed_by UUID,
  reason VARCHAR(100) NOT NULL,
  notes TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_movement_input        ON input_movement(input_id);
CREATE INDEX idx_movement_task         ON input_movement(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX idx_movement_occurred_at  ON input_movement(occurred_at DESC);

-- ---------------------------------------------------------------------------
-- Vista con stock calculado al vuelo. Para listados normales preferimos el
-- SELECT explicito en repos, pero la vista facilita queries ad-hoc.
-- ---------------------------------------------------------------------------
CREATE VIEW input_with_stock AS
SELECT
  i.*,
  COALESCE((SELECT SUM(CASE WHEN m.kind='IN' THEN m.quantity ELSE -m.quantity END)
              FROM input_movement m WHERE m.input_id = i.id), 0) AS current_stock
  FROM input i;

-- ---------------------------------------------------------------------------
-- Anti-spam de stock-low: registro de emisiones, no se reemite > 1 cada 24h.
-- ---------------------------------------------------------------------------
CREATE TABLE stock_alert_log (
  input_id UUID PRIMARY KEY REFERENCES input(id) ON DELETE CASCADE,
  last_emitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
  last_threshold  NUMERIC(14,3) NOT NULL,
  last_stock      NUMERIC(14,3) NOT NULL,
  is_currently_below BOOLEAN NOT NULL DEFAULT TRUE
);
