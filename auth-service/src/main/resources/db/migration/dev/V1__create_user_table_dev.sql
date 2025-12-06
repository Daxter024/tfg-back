CREATE TABLE role (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO role (name) VALUES
('admin'),
('user');

CREATE TABLE users (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role_id INT,

    CONSTRAINT fk_role
        FOREIGN KEY(role_id)
        REFERENCES role(id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);