CREATE TABLE digital_team_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    owner_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_business_id UNIQUE (business_id),
    CONSTRAINT uk_tenant_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE digital_team_tenant_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_tenant_user_tenant FOREIGN KEY (tenant_id)
        REFERENCES digital_team_tenant (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tenant_user_user ON digital_team_tenant_user (user_id, tenant_id);
