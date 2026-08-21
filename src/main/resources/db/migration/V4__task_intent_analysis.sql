CREATE TABLE digital_team_coordinator_analysis (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    input_snapshot TEXT NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    decision_type VARCHAR(32) NOT NULL,
    decision_json TEXT NOT NULL,
    repaired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_project FOREIGN KEY (project_id) REFERENCES digital_team_project (id)
);

CREATE INDEX idx_analysis_project_created
    ON digital_team_coordinator_analysis (tenant_id, project_id, created_at);

CREATE TABLE digital_team_coordinator_human_request (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    analysis_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT fk_human_request_analysis FOREIGN KEY (analysis_id)
        REFERENCES digital_team_coordinator_analysis (id),
    CONSTRAINT fk_human_request_project FOREIGN KEY (project_id) REFERENCES digital_team_project (id)
);

CREATE INDEX idx_human_request_pending
    ON digital_team_coordinator_human_request (tenant_id, project_id, status, created_at);
