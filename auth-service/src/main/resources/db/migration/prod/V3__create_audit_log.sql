CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    action VARCHAR(64) NOT NULL,
    target_user_id UUID,
    before_value JSONB,
    after_value JSONB,
    ip VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_target ON audit_log(target_user_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
