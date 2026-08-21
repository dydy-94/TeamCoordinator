CREATE TABLE digital_team_project (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_project_tenant ON digital_team_project (tenant_id, id);

CREATE TABLE digital_team_project_member (
    project_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES digital_team_project (id)
);

CREATE INDEX idx_project_member_tenant_user
    ON digital_team_project_member (tenant_id, user_id, project_id);

CREATE TABLE digital_team_project_expert (
    project_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    expert_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, expert_id),
    CONSTRAINT fk_project_expert_project FOREIGN KEY (project_id) REFERENCES digital_team_project (id)
);

CREATE INDEX idx_project_expert_tenant ON digital_team_project_expert (tenant_id, project_id);

CREATE TABLE digital_team_permission_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_id VARCHAR(128),
    detail VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permission_audit_project
    ON digital_team_permission_audit_log (tenant_id, project_id, created_at);
