ALTER TABLE human_request
    ADD COLUMN agent_question_id VARCHAR(128) NULL AFTER question;
