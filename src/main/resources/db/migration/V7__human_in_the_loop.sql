CREATE TABLE human_request (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    analysis_id VARCHAR(64),
    task_id VARCHAR(64),
    message_id VARCHAR(64),
    dispatch_id VARCHAR(64),
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(32) NOT NULL,
    question TEXT NOT NULL,
    allowed_roles VARCHAR(128) NOT NULL,
    input_schema TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decision VARCHAR(32),
    response_json TEXT,
    response_idempotency_key VARCHAR(128),
    responded_by VARCHAR(64),
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT uk_human_response_idempotency
        UNIQUE (tenant_id, response_idempotency_key),
    CONSTRAINT fk_human_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_human_analysis FOREIGN KEY (analysis_id)
        REFERENCES coordinator_analysis (id),
    CONSTRAINT fk_human_task FOREIGN KEY (task_id) REFERENCES coordinator_task (id)
);

CREATE INDEX idx_human_pending
    ON human_request (tenant_id, project_id, status, expires_at);
