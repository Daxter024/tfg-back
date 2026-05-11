CREATE TABLE threshold (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sensor_id UUID REFERENCES sensor(id) ON DELETE CASCADE,
  variable  variable_kind,
  min_value NUMERIC(14,4),
  max_value NUMERIC(14,4),
  notify_user_ids UUID[] NOT NULL DEFAULT '{}',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT threshold_target_xor CHECK (
    (sensor_id IS NOT NULL AND variable IS NULL) OR
    (sensor_id IS NULL     AND variable IS NOT NULL)
  ),
  CONSTRAINT threshold_bounds_meaningful CHECK (
    min_value IS NOT NULL OR max_value IS NOT NULL
  ),
  CONSTRAINT threshold_bounds_ordered CHECK (
    min_value IS NULL OR max_value IS NULL OR min_value <= max_value
  )
);
CREATE INDEX idx_threshold_sensor   ON threshold(sensor_id) WHERE sensor_id IS NOT NULL;
CREATE INDEX idx_threshold_variable ON threshold(variable)  WHERE variable IS NOT NULL;

CREATE TYPE alert_state AS ENUM ('new','reviewed','resolved');
CREATE TYPE alert_kind  AS ENUM ('below_min','above_max');

CREATE TABLE sensor_alert (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sensor_id UUID NOT NULL REFERENCES sensor(id) ON DELETE CASCADE,
  threshold_id UUID NOT NULL REFERENCES threshold(id),
  kind alert_kind NOT NULL,
  first_value NUMERIC(14,4) NOT NULL,
  first_recorded_at TIMESTAMPTZ NOT NULL,
  last_recorded_at  TIMESTAMPTZ NOT NULL,
  reading_count INTEGER NOT NULL DEFAULT 1 CHECK (reading_count > 0),
  state alert_state NOT NULL DEFAULT 'new',
  comment TEXT,
  reviewed_by UUID,
  reviewed_at TIMESTAMP,
  resolved_at TIMESTAMP
);
CREATE INDEX idx_alert_sensor_open ON sensor_alert(sensor_id) WHERE state <> 'resolved';
CREATE INDEX idx_alert_created     ON sensor_alert(first_recorded_at DESC);
