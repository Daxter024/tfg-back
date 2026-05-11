CREATE TABLE task_evidence (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes <= 10 * 1024 * 1024),
  storage_key VARCHAR(512) NOT NULL,
  uploaded_by UUID NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_task_evidence_task ON task_evidence(task_id);
