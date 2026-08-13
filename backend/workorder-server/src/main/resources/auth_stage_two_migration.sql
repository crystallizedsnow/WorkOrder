CREATE TABLE channel_binding_challenge (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL,
    code_hash VARCHAR(44) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_channel_challenge_hash (code_hash),
    KEY idx_channel_challenge_user (user_id, platform, status),
    CONSTRAINT fk_channel_challenge_user FOREIGN KEY (user_id) REFERENCES staff(id)
);

CREATE TABLE external_identity_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    platform VARCHAR(32) NOT NULL,
    tenant_key VARCHAR(128) NOT NULL,
    union_id VARCHAR(128) NOT NULL,
    open_id VARCHAR(128) NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    binding_version INT NOT NULL DEFAULT 1,
    bound_at DATETIME NOT NULL,
    unbound_at DATETIME NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_external_identity (platform, tenant_key, union_id),
    KEY idx_external_binding_user (platform, user_id, status),
    CONSTRAINT fk_external_binding_user FOREIGN KEY (user_id) REFERENCES staff(id)
);
