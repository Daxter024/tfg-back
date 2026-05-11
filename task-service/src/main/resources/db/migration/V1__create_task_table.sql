CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Catalogo de tipos de tarea (i18n por label_key)
CREATE TABLE task_type (
  id SERIAL PRIMARY KEY,
  code VARCHAR(50) UNIQUE NOT NULL,
  label_key VARCHAR(100) NOT NULL
);
INSERT INTO task_type (code, label_key) VALUES
  ('SOWING','task.type.sowing'),
  ('IRRIGATION','task.type.irrigation'),
  ('FERTILIZATION','task.type.fertilization'),
  ('TREATMENT','task.type.treatment'),
  ('HARVEST','task.type.harvest'),
  ('OTHER','task.type.other');

CREATE TABLE task (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_type_id INTEGER NOT NULL REFERENCES task_type(id),

  -- Unica referencia geografica: terrain_id (parcels NO existen, decision D1+sin-parcels)
  terrain_id UUID NOT NULL,

  -- Planificacion
  planned_at TIMESTAMP NOT NULL,
  estimated_duration_minutes INTEGER NOT NULL CHECK (estimated_duration_minutes > 0),

  -- Ejecucion
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (state IN ('PENDING','IN_PROGRESS','FINISHED','CANCELLED')),
  started_at  TIMESTAMP,
  finished_at TIMESTAMP,
  real_duration_minutes INTEGER,

  -- Asignacion (referencia logica a auth_db.users)
  created_by   UUID NOT NULL,
  assigned_to  UUID NOT NULL,

  -- Recurrencia: plantilla + instancias hijas
  recurrence_parent_id UUID REFERENCES task(id) ON DELETE SET NULL,
  recurrence_rule VARCHAR(100),

  notes TEXT,
  planned_inputs  JSONB,
  consumed_inputs JSONB,

  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP,

  CONSTRAINT task_finished_after_started CHECK (
    finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at
  )
);
CREATE INDEX idx_task_terrain        ON task(terrain_id);
CREATE INDEX idx_task_assigned       ON task(assigned_to);
CREATE INDEX idx_task_created_by     ON task(created_by);
CREATE INDEX idx_task_state_planned  ON task(state, planned_at);

-- Historial de transiciones de estado
CREATE TABLE task_state_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
  from_state VARCHAR(16),
  to_state   VARCHAR(16) NOT NULL,
  changed_by UUID NOT NULL,
  changed_at TIMESTAMP NOT NULL DEFAULT NOW(),
  note TEXT
);
CREATE INDEX idx_task_state_history_task ON task_state_history(task_id);
