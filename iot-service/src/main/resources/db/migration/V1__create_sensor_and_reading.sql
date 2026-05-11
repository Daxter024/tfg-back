CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE variable_kind AS ENUM (
  'temperature','humidity','ph','soil_moisture','wind_speed','rainfall','luminosity','other'
);

CREATE TABLE sensor (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  external_id VARCHAR(100) UNIQUE,
  variable variable_kind NOT NULL,
  unit VARCHAR(16) NOT NULL,
  terrain_id UUID NOT NULL,
  expected_interval_seconds INTEGER NOT NULL DEFAULT 300 CHECK (expected_interval_seconds > 0),
  status VARCHAR(16) NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','inactive','no_signal')),
  created_by UUID NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  last_reading_at TIMESTAMP
);
CREATE INDEX idx_sensor_terrain  ON sensor(terrain_id);
CREATE INDEX idx_sensor_variable ON sensor(variable);
CREATE INDEX idx_sensor_status   ON sensor(status);

CREATE TABLE sensor_reading (
  sensor_id UUID NOT NULL REFERENCES sensor(id) ON DELETE CASCADE,
  recorded_at TIMESTAMPTZ NOT NULL,
  value NUMERIC(14,4) NOT NULL,
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (sensor_id, recorded_at)
);
CREATE INDEX idx_reading_recorded_at ON sensor_reading(recorded_at DESC);

-- Plain views for hourly/daily aggregation. They will be replaced by Timescale
-- continuous aggregates if the optional V99 placeholder is promoted to V3.
CREATE VIEW sensor_reading_hourly AS
SELECT
  sensor_id,
  date_trunc('hour', recorded_at) AS bucket,
  AVG(value) AS avg_value,
  MIN(value) AS min_value,
  MAX(value) AS max_value,
  COUNT(*)    AS samples
FROM sensor_reading
GROUP BY sensor_id, date_trunc('hour', recorded_at);

CREATE VIEW sensor_reading_daily AS
SELECT
  sensor_id,
  date_trunc('day', recorded_at) AS bucket,
  AVG(value) AS avg_value,
  MIN(value) AS min_value,
  MAX(value) AS max_value,
  COUNT(*)    AS samples
FROM sensor_reading
GROUP BY sensor_id, date_trunc('day', recorded_at);

-- Device authentication: one API key per sensor (or N if rotated).
CREATE TABLE device_api_key (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sensor_id UUID NOT NULL REFERENCES sensor(id) ON DELETE CASCADE,
  key_hash VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  rotated_from UUID REFERENCES device_api_key(id),
  CONSTRAINT uq_active_per_sensor UNIQUE (sensor_id, key_hash)
);
CREATE INDEX idx_device_api_key_sensor ON device_api_key(sensor_id) WHERE active;
