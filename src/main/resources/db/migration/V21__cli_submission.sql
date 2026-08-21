-- V21: CLI submission channel.
-- Structured Coordinator outputs (decision / plan / verdict) submitted by the
-- companion CLI are stored here, keyed by the AgentCore session the agent was
-- running in. Consumers treat these as a higher-priority source than the
-- run's streamed output (toolUsed input / end content).
CREATE TABLE digital_team_coordinator_cli_submission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cli_submission UNIQUE (session_id, kind)
);

CREATE INDEX idx_cli_submission_session ON digital_team_coordinator_cli_submission (session_id);
