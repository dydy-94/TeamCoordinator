-- ============================================================================
-- TeamCoordinator 完整数据库定义（MySQL 8）
-- ============================================================================
-- 本文件是全部 Flyway 迁移（V1 ~ V25，含 Java 迁移）执行后的最终表结构快照，
-- 可直接用于全新环境的一次性建库初始化（等价于 Flyway 全量执行后的 schema）。
--
-- 使用说明:
--   1. 项目已脱离 Flyway：应用默认不执行迁移（application.yml
--      flyway.enabled=${FLYWAY_ENABLED:false}）。数据库初始化由本目录完成：
--      docker compose 首次启动时会把 db/init/*.sql 按序挂载到
--      /docker-entrypoint-initdb.d 自动执行（01-schema.sql 建表、
--      02-seed.sql 灌种子）。独立建库时手动按序执行这两个文件即可。
--   2. 测试环境（application-test.yml）仍启用 Flyway 自建 H2。
--   3. 关键结构事实：V13 之后所有表以 BIGINT 自增 id 为代理主键、
--      字符串 business_id 为对外业务主键（API 只暴露 business_id）。
--      project_message.idempotency_key 已在 V13 删除。
-- ============================================================================

-- 1. 版本标记
CREATE TABLE digital_team_schema_version_marker (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(96) NOT NULL,
    marker VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_schema_version_marker_business_id UNIQUE (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 权限审计日志
CREATE TABLE digital_team_permission_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(96) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_id VARCHAR(128),
    detail VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_permission_audit_log_business_id UNIQUE (business_id),
    INDEX idx_permission_audit_project (tenant_id, project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 项目
CREATE TABLE digital_team_project (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    coordinator_agent_id VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uk_project_business_id UNIQUE (business_id),
    INDEX idx_project_tenant (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 项目成员
CREATE TABLE digital_team_project_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_member_business UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    INDEX idx_project_member_tenant_user (tenant_id, user_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 项目专家
CREATE TABLE digital_team_project_expert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    expert_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_expert_business UNIQUE (project_id, expert_id),
    CONSTRAINT fk_project_expert_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    INDEX idx_project_expert_tenant (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 会话任务（用户创建的 task）
CREATE TABLE digital_team_project_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    coordinator_session_id VARCHAR(128),
    coordinator_agent_id VARCHAR(128),
    title VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conversation_session UNIQUE (tenant_id, session_id),
    CONSTRAINT uk_project_conversation_business_id UNIQUE (business_id),
    CONSTRAINT fk_conversation_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 用户消息
CREATE TABLE digital_team_project_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    client_message_id VARCHAR(128) NOT NULL,
    message_text TEXT NOT NULL,
    attachment_refs TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_message_client UNIQUE (tenant_id, project_id, client_message_id),
    CONSTRAINT uk_project_message_business_id UNIQUE (business_id),
    CONSTRAINT fk_message_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES digital_team_project_conversation (business_id),
    INDEX idx_message_project_created (tenant_id, project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 项目事件序列号
CREATE TABLE digital_team_project_event_sequence (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    next_sequence BIGINT NOT NULL,
    CONSTRAINT uk_project_event_sequence_business UNIQUE (tenant_id, project_id),
    CONSTRAINT fk_event_sequence_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 面向前端的事件（持久化，支持 Last-Event-ID 重放）
CREATE TABLE digital_team_project_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_event_sequence UNIQUE (tenant_id, conversation_id, sequence),
    CONSTRAINT uk_project_event_business_id UNIQUE (business_id),
    CONSTRAINT fk_event_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    CONSTRAINT fk_event_conversation FOREIGN KEY (conversation_id)
        REFERENCES digital_team_project_conversation (business_id),
    INDEX idx_event_replay (tenant_id, project_id, visibility, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 会话事件序列号
CREATE TABLE digital_team_conversation_event_sequence (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    next_sequence BIGINT NOT NULL,
    CONSTRAINT uk_conversation_event_sequence_business UNIQUE (tenant_id, conversation_id),
    CONSTRAINT fk_conversation_event_sequence FOREIGN KEY (conversation_id)
        REFERENCES digital_team_project_conversation (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. 执行票（worker 领取的调度单元）
CREATE TABLE digital_team_coordinator_dispatch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMP NULL,
    last_error VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dispatch_message UNIQUE (tenant_id, project_id, message_id),
    CONSTRAINT uk_coordinator_dispatch_business_id UNIQUE (business_id),
    CONSTRAINT fk_dispatch_message FOREIGN KEY (message_id)
        REFERENCES digital_team_project_message (business_id),
    INDEX idx_dispatch_pending (status, available_at),
    INDEX idx_dispatch_lease (status, available_at, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. 意图分析记录
CREATE TABLE digital_team_coordinator_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    input_snapshot TEXT NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    decision_type VARCHAR(32) NOT NULL,
    decision_json TEXT NOT NULL,
    repaired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coordinator_analysis_business_id UNIQUE (business_id),
    CONSTRAINT fk_analysis_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    INDEX idx_analysis_project_created (tenant_id, project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. 协调者提问记录
CREATE TABLE digital_team_coordinator_human_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    analysis_id VARCHAR(64),
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT uk_coordinator_human_request_business_id UNIQUE (business_id),
    CONSTRAINT fk_human_request_analysis FOREIGN KEY (analysis_id)
        REFERENCES digital_team_coordinator_analysis (business_id),
    CONSTRAINT fk_human_request_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    INDEX idx_human_request_pending (tenant_id, project_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. 执行计划
CREATE TABLE digital_team_coordinator_plan (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    analysis_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    plan_version INT NOT NULL DEFAULT 1,
    intent_json TEXT,
    plan_json TEXT,
    repair_count INT NOT NULL DEFAULT 0,
    supersedes_plan_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plan_message_version
        UNIQUE (tenant_id, project_id, message_id, plan_version),
    CONSTRAINT uk_coordinator_plan_business_id UNIQUE (business_id),
    CONSTRAINT fk_plan_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    CONSTRAINT fk_plan_message FOREIGN KEY (message_id)
        REFERENCES digital_team_project_message (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. 协调子任务（专家执行单元）
CREATE TABLE digital_team_coordinator_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    task_key VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    expert_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    objective TEXT NOT NULL,
    attachment_refs TEXT,
    result_json TEXT,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMP NULL,
    dependencies TEXT,
    required_capabilities TEXT,
    expected_output VARCHAR(512),
    acceptance_criteria VARCHAR(1024),
    correction_of VARCHAR(64),
    correction_count INT NOT NULL DEFAULT 0,
    result_accepted BOOLEAN,
    reused_from_task_id VARCHAR(64),
    consecutive_failures INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_plan_key UNIQUE (plan_id, task_key),
    CONSTRAINT uk_task_request UNIQUE (tenant_id, request_id),
    CONSTRAINT uk_coordinator_task_business_id UNIQUE (business_id),
    CONSTRAINT fk_task_plan FOREIGN KEY (plan_id)
        REFERENCES digital_team_coordinator_plan (business_id),
    INDEX idx_task_session (session_id),
    INDEX idx_task_lease (status, lease_expires_at),
    INDEX idx_task_plan_status (plan_id, status, task_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. 专家任务事件（当前基本闲置：事件事实源在 AgentCore）
CREATE TABLE digital_team_coordinator_task_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(96) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_event_id UNIQUE (tenant_id, event_id),
    CONSTRAINT uk_coordinator_task_event_business_id UNIQUE (business_id),
    CONSTRAINT fk_task_event_task FOREIGN KEY (task_id)
        REFERENCES digital_team_coordinator_task (business_id),
    INDEX idx_task_event_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. 人类请求（协调者提问 / 专家求助共用）
CREATE TABLE digital_team_human_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    analysis_id VARCHAR(64),
    task_id VARCHAR(64),
    message_id VARCHAR(64),
    dispatch_id VARCHAR(64),
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(32) NOT NULL,
    question TEXT NOT NULL,
    agent_question_id VARCHAR(128),
    allowed_roles VARCHAR(128) NOT NULL,
    input_schema TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decision VARCHAR(32),
    response_json TEXT,
    response_idempotency_key VARCHAR(128),
    responded_by VARCHAR(64),
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT uk_human_response_idempotency
        UNIQUE (tenant_id, response_idempotency_key),
    CONSTRAINT uk_human_request_business_id UNIQUE (business_id),
    CONSTRAINT fk_human_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    CONSTRAINT fk_human_analysis FOREIGN KEY (analysis_id)
        REFERENCES digital_team_coordinator_analysis (business_id),
    CONSTRAINT fk_human_task FOREIGN KEY (task_id)
        REFERENCES digital_team_coordinator_task (business_id),
    INDEX idx_human_pending (tenant_id, project_id, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 18. 产物
CREATE TABLE digital_team_project_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    expert_run_id VARCHAR(128),
    version INT NOT NULL DEFAULT 1,
    storage_key VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT,
    sha256 VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADING',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT uk_artifact_storage UNIQUE (storage_key),
    CONSTRAINT uk_artifact_version UNIQUE (project_id, file_name, version),
    CONSTRAINT uk_project_artifact_business_id UNIQUE (business_id),
    CONSTRAINT fk_artifact_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    CONSTRAINT fk_artifact_task FOREIGN KEY (task_id)
        REFERENCES digital_team_coordinator_task (business_id),
    INDEX idx_artifact_project_status (tenant_id, project_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 19. 产物血缘（输出 ← 输入）
CREATE TABLE digital_team_project_artifact_lineage (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    output_artifact_id VARCHAR(64) NOT NULL,
    input_artifact_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_artifact_lineage_business
        UNIQUE (output_artifact_id, input_artifact_id),
    CONSTRAINT fk_lineage_output FOREIGN KEY (output_artifact_id)
        REFERENCES digital_team_project_artifact (business_id),
    CONSTRAINT fk_lineage_input FOREIGN KEY (input_artifact_id)
        REFERENCES digital_team_project_artifact (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 20. coordinator agent run 记录
CREATE TABLE digital_team_coordinator_agent_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    run_key VARCHAR(128) NOT NULL,
    session_id VARCHAR(128),
    business_session_id VARCHAR(128),
    stage VARCHAR(16) NOT NULL DEFAULT 'ANALYZE',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_sequence BIGINT NOT NULL DEFAULT 0,
    context_json TEXT NOT NULL,
    invalid_output TEXT,
    output_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coordinator_agent_run_key UNIQUE (tenant_id, run_key),
    CONSTRAINT uk_coordinator_agent_run_business_id UNIQUE (business_id),
    CONSTRAINT fk_coordinator_agent_run_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    INDEX idx_coordinator_agent_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 21. 提示词模板（数据库管理，PUBLISHED/DRAFT 版本化）
CREATE TABLE digital_team_prompt_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    prompt_key VARCHAR(128) NOT NULL,
    agent_scope VARCHAR(128) NOT NULL,
    scene VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    template_content TEXT NOT NULL,
    variables_schema TEXT,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    CONSTRAINT uk_prompt_version UNIQUE (prompt_key, version),
    CONSTRAINT uk_prompt_template_business_id UNIQUE (business_id),
    INDEX idx_prompt_active (prompt_key, status, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 22. 提示词执行审计
CREATE TABLE digital_team_prompt_execution (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    invocation_id VARCHAR(128) NOT NULL,
    agent_id VARCHAR(128) NOT NULL,
    scene VARCHAR(64) NOT NULL,
    prompt_template_id VARCHAR(64) NOT NULL,
    prompt_version INT NOT NULL,
    rendered_prompt TEXT NOT NULL,
    variables_snapshot TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prompt_invocation UNIQUE (invocation_id, scene),
    CONSTRAINT uk_prompt_execution_business_id UNIQUE (business_id),
    CONSTRAINT fk_prompt_execution_template FOREIGN KEY (prompt_template_id)
        REFERENCES digital_team_prompt_template (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 23. 会话-专家 session 复用映射
CREATE TABLE digital_team_project_conversation_expert_session (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    expert_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_expert_session_conversation UNIQUE (conversation_id, expert_id),
    INDEX idx_expert_session_lookup (tenant_id, project_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 24. 技能目录
CREATE TABLE digital_team_skill (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    prompt TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 25. 项目技能挂载
CREATE TABLE digital_team_project_skill (
    project_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, skill_id),
    CONSTRAINT fk_project_skill_project FOREIGN KEY (project_id)
        REFERENCES digital_team_project (business_id),
    CONSTRAINT fk_project_skill_skill FOREIGN KEY (skill_id)
        REFERENCES digital_team_skill (business_id),
    INDEX idx_project_skill_tenant (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 26. CLI 提交载荷（按 task_id+kind 幂等，消费后删除）
CREATE TABLE digital_team_coordinator_cli_submission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cli_submission UNIQUE (task_id, kind),
    INDEX idx_cli_submission_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
