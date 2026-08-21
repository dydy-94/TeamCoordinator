CREATE TABLE digital_team_project_artifact (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    expert_run_id VARCHAR(128),
    version INT NOT NULL DEFAULT 1,
    storage_key VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT,
    sha256 VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADING',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT uk_artifact_storage UNIQUE (storage_key),
    CONSTRAINT uk_artifact_version UNIQUE (project_id, file_name, version),
    CONSTRAINT fk_artifact_project FOREIGN KEY (project_id) REFERENCES digital_team_project (id),
    CONSTRAINT fk_artifact_task FOREIGN KEY (task_id) REFERENCES digital_team_coordinator_task (id)
);

CREATE INDEX idx_artifact_project_status
    ON digital_team_project_artifact (tenant_id, project_id, status, created_at);

CREATE TABLE digital_team_project_artifact_lineage (
    output_artifact_id VARCHAR(64) NOT NULL,
    input_artifact_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (output_artifact_id, input_artifact_id),
    CONSTRAINT fk_lineage_output FOREIGN KEY (output_artifact_id)
        REFERENCES digital_team_project_artifact (id),
    CONSTRAINT fk_lineage_input FOREIGN KEY (input_artifact_id)
        REFERENCES digital_team_project_artifact (id)
);
