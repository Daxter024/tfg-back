CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE role(
    id SERIAL PRIMARY KEY ,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO role (name) VALUES
('admin'),
('user');

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role_id INTEGER REFERENCES role(id),

    CONSTRAINT fk_role
    FOREIGN KEY(role_id)
    REFERENCES role(id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);