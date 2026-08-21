ALTER TABLE digital_team_coordinator_plan ADD COLUMN plan_json TEXT;
ALTER TABLE digital_team_coordinator_plan ADD COLUMN repair_count INT NOT NULL DEFAULT 0;
ALTER TABLE digital_team_coordinator_plan ADD COLUMN supersedes_plan_id VARCHAR(64);

ALTER TABLE digital_team_coordinator_task ADD COLUMN dependencies TEXT;
ALTER TABLE digital_team_coordinator_task ADD COLUMN required_capabilities TEXT;
ALTER TABLE digital_team_coordinator_task ADD COLUMN expected_output VARCHAR(512);
ALTER TABLE digital_team_coordinator_task ADD COLUMN acceptance_criteria VARCHAR(1024);
ALTER TABLE digital_team_coordinator_task ADD COLUMN correction_of VARCHAR(64);
ALTER TABLE digital_team_coordinator_task ADD COLUMN correction_count INT NOT NULL DEFAULT 0;
ALTER TABLE digital_team_coordinator_task ADD COLUMN result_accepted BOOLEAN;
ALTER TABLE digital_team_coordinator_task ADD COLUMN reused_from_task_id VARCHAR(64);

CREATE INDEX idx_task_plan_status
    ON digital_team_coordinator_task (plan_id, status, task_key);
