ALTER TABLE project_conversation
    ADD COLUMN coordinator_session_id VARCHAR(128) NULL AFTER session_id;

CREATE TABLE project_conversation_expert_session (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    expert_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_expert_session_conversation UNIQUE (conversation_id, expert_id)
);

CREATE INDEX idx_expert_session_lookup
    ON project_conversation_expert_session (tenant_id, project_id, conversation_id);
