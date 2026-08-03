CREATE TABLE project_conversation (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conversation_project UNIQUE (tenant_id, project_id),
    CONSTRAINT fk_conversation_project FOREIGN KEY (project_id) REFERENCES project (id)
);

CREATE TABLE project_message (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    client_message_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    message_text TEXT NOT NULL,
    attachment_refs TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_message_client UNIQUE (tenant_id, project_id, client_message_id),
    CONSTRAINT uk_message_idempotency UNIQUE (tenant_id, project_id, idempotency_key),
    CONSTRAINT fk_message_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES project_conversation (id)
);

CREATE INDEX idx_message_project_created
    ON project_message (tenant_id, project_id, created_at);

CREATE TABLE project_event_sequence (
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    next_sequence BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, project_id),
    CONSTRAINT fk_event_sequence_project FOREIGN KEY (project_id) REFERENCES project (id)
);

CREATE TABLE project_event (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_event_sequence UNIQUE (tenant_id, project_id, sequence),
    CONSTRAINT fk_event_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_event_conversation FOREIGN KEY (conversation_id)
        REFERENCES project_conversation (id)
);

CREATE INDEX idx_event_replay
    ON project_event (tenant_id, project_id, visibility, sequence);

CREATE TABLE coordinator_dispatch (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dispatch_message UNIQUE (tenant_id, project_id, message_id),
    CONSTRAINT fk_dispatch_message FOREIGN KEY (message_id) REFERENCES project_message (id)
);

CREATE INDEX idx_dispatch_pending ON coordinator_dispatch (status, available_at);
