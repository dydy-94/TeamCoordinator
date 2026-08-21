ALTER TABLE digital_team_project
    ADD COLUMN IF NOT EXISTS coordinator_agent_id VARCHAR(128) NULL AFTER description;
ALTER TABLE digital_team_project_conversation
    ADD COLUMN IF NOT EXISTS coordinator_agent_id VARCHAR(128) NULL AFTER coordinator_session_id;
