-- Roles funcionales del plan (matriz de permisos EP-USR).
-- Se mantienen 'admin' y 'user' existentes para no romper fixtures previas;
-- el código nuevo usa exclusivamente los nombres funcionales.
INSERT INTO role (name) VALUES ('agricultor'), ('tecnico'), ('administrador')
    ON CONFLICT (name) DO NOTHING;

-- El DTO de registro y la lógica funcional usan 'full_name'; alineamos columna.
ALTER TABLE users RENAME COLUMN username TO full_name;

-- Estado + metadatos de usuario (HU-USR-03 deactivate, HU-USR-02 auditoría/lock).
ALTER TABLE users
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'inactive')),
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN last_login_at TIMESTAMP,
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP;

CREATE INDEX idx_users_status ON users(status);
