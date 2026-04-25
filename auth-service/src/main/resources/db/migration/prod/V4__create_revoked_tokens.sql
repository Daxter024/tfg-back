CREATE TABLE revoked_token (
    jti UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    revoked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_revoked_token_expires ON revoked_token(expires_at);
CREATE INDEX idx_revoked_token_user ON revoked_token(user_id);
