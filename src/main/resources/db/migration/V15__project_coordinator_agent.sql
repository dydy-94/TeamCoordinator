ALTER TABLE project
    ADD COLUMN IF NOT EXISTS coordinator_agent_id VARCHAR(128) NULL AFTER description;
ALTER TABLE project_conversation
    ADD COLUMN IF NOT EXISTS coordinator_agent_id VARCHAR(128) NULL AFTER coordinator_session_id;
