-- Apply before deploying authentication stage one. Back up the database first.
ALTER TABLE staff ADD COLUMN auth_version INT NOT NULL DEFAULT 0 COMMENT 'Authentication version';
ALTER TABLE staff MODIFY COLUMN password VARCHAR(100) NOT NULL COMMENT 'BCrypt password hash';

CREATE TABLE auth_refresh_session (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(44) NOT NULL,
    auth_version INT NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    revoked_at DATETIME NULL,
    replaced_by_session_id VARCHAR(36) NULL,
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
    KEY idx_auth_refresh_family (family_id),
    KEY idx_auth_refresh_user (user_id),
    CONSTRAINT fk_auth_refresh_user FOREIGN KEY (user_id) REFERENCES staff(id)
);

-- Existing plaintext passwords are upgraded to BCrypt after the next successful login.
