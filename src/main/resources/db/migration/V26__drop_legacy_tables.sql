-- 清理不再使用的遗留表：
--   digital_team_coordinator_task_event   专家事件落库（事件事实源在 AgentCore，写入路径已移除）
--   digital_team_coordinator_human_request 被 digital_team_human_request 取代（V7）
--   digital_team_project_event_sequence    被 digital_team_conversation_event_sequence 取代（V10）
--   digital_team_schema_version_marker     仅建表从未使用
DROP TABLE IF EXISTS digital_team_coordinator_task_event;
DROP TABLE IF EXISTS digital_team_coordinator_human_request;
DROP TABLE IF EXISTS digital_team_project_event_sequence;
DROP TABLE IF EXISTS digital_team_schema_version_marker;
