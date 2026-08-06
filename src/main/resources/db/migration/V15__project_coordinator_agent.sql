ALTER TABLE project
    ADD COLUMN IF NOT EXISTS coordinator_agent_id VARCHAR(128) NULL AFTER description;
