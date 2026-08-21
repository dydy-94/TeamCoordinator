CREATE TABLE digital_team_coordinator_agent_run (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    run_key VARCHAR(128) NOT NULL,
    session_id VARCHAR(128),
    stage VARCHAR(16) NOT NULL DEFAULT 'ANALYZE',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_sequence BIGINT NOT NULL DEFAULT 0,
    context_json TEXT NOT NULL,
    invalid_output TEXT,
    output_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coordinator_agent_run_key UNIQUE (tenant_id, run_key),
    CONSTRAINT fk_coordinator_agent_run_project FOREIGN KEY (project_id) REFERENCES digital_team_project (id)
);

CREATE INDEX idx_coordinator_agent_session
    ON digital_team_coordinator_agent_run (session_id);
