CREATE TABLE notification_preference (
  user_id UUID PRIMARY KEY,
  email_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
  in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  default_lead_minutes INTEGER NOT NULL DEFAULT 1440,
  task_type_lead_minutes JSONB,
  quiet_hours_start TIME,
  quiet_hours_end   TIME,
  also_notify_creator BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE notification (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  -- task_id nullable: hay notifs que no vienen de una task (stock-low, sensor-alert)
  task_id UUID REFERENCES task(id) ON DELETE CASCADE,
  source_kind VARCHAR(32) NOT NULL,
  source_ref  UUID,
  channel VARCHAR(16) NOT NULL,
  title TEXT NOT NULL,
  body  TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  read_at TIMESTAMP
);
CREATE INDEX idx_notification_user_unread ON notification(user_id) WHERE read_at IS NULL;
CREATE INDEX idx_notification_created_at  ON notification(created_at DESC);

-- Anti-spam log: una alerta de stock-low por input cada 24 h maximo
CREATE TABLE notification_emission_log (
  id BIGSERIAL PRIMARY KEY,
  source_kind VARCHAR(32) NOT NULL,
  source_ref  UUID NOT NULL,
  user_id     UUID NOT NULL,
  emitted_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_emission_recent ON notification_emission_log(source_kind, source_ref, user_id, emitted_at DESC);
