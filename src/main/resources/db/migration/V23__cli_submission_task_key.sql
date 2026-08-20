-- V23: Key CLI submissions by task id instead of the AgentCore session id.
-- The AgentCore runtime cannot reliably perceive its own session id; the
-- task id is the identifier shared by Coordinator, AgentCore and CLI.
ALTER TABLE coordinator_cli_submission RENAME COLUMN session_id TO task_id;

DROP INDEX idx_cli_submission_session;
CREATE INDEX idx_cli_submission_task ON coordinator_cli_submission (task_id);
