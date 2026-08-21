ALTER TABLE digital_team_coordinator_dispatch ADD COLUMN lease_owner VARCHAR(128);
ALTER TABLE digital_team_coordinator_dispatch ADD COLUMN lease_expires_at TIMESTAMP NULL;
ALTER TABLE digital_team_coordinator_dispatch ADD COLUMN last_error VARCHAR(1024);

CREATE INDEX idx_dispatch_lease
    ON digital_team_coordinator_dispatch (status, available_at, lease_expires_at);

CREATE TABLE digital_team_coordinator_plan (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    analysis_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    plan_version INT NOT NULL DEFAULT 1,
    intent_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plan_message UNIQUE (tenant_id, project_id, message_id),
    CONSTRAINT fk_plan_project FOREIGN KEY (project_id) REFERENCES digital_team_project (id),
    CONSTRAINT fk_plan_message FOREIGN KEY (message_id) REFERENCES digital_team_project_message (id)
);

CREATE TABLE digital_team_coordinator_task (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    task_key VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    expert_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    objective TEXT NOT NULL,
    attachment_refs TEXT,
    result_json TEXT,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_plan_key UNIQUE (plan_id, task_key),
    CONSTRAINT uk_task_request UNIQUE (tenant_id, request_id),
    CONSTRAINT fk_task_plan FOREIGN KEY (plan_id) REFERENCES digital_team_coordinator_plan (id)
);

CREATE INDEX idx_task_session ON digital_team_coordinator_task (session_id);
CREATE INDEX idx_task_lease ON digital_team_coordinator_task (status, lease_expires_at);

CREATE TABLE digital_team_coordinator_task_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_event_id UNIQUE (tenant_id, event_id),
    CONSTRAINT uk_task_event_sequence UNIQUE (task_id, sequence),
    CONSTRAINT fk_task_event_task FOREIGN KEY (task_id) REFERENCES digital_team_coordinator_task (id)
);
