# 数据库表详解

> 本文档覆盖 TeamCoordinator 使用的全部 25 张 MySQL 表(均带 `digital_team_` 前缀),
> 逐一说明每个字段的**含义、用途与生成来源**。schema 的权威定义是
> `db/init/01-schema.sql`(等价于 Flyway V1~V28 全量执行后的最终结构;
> V26 清除了 4 张不再使用的遗留表,V27 新增租户与租户成员表,V28 新增平台管理员表)。

## 全局约定

- **代理主键 `id`**:BIGINT 自增,仅供数据库内部/排序使用,API 与业务代码一律不暴露。
- **业务主键 `business_id`**:字符串 UUID(格式 `<前缀>-<uuid>`),所有 API、外键、事件 payload
  只使用它。生成前缀见各表"来源"列。
- **`tenant_id`**:租户隔离维度,取自请求头 `X-Tenant-Id`;`user_id`/`created_by`/`actor_user_id`
  取自请求头 `X-User-Id`。
- **时间戳**:`created_at` 由数据库 `DEFAULT CURRENT_TIMESTAMP` 生成;`updated_at`/
  `resolved_at`/`completed_at`/`published_at`/`expires_at` 由业务 SQL 显式写入。
- 所有表 InnoDB + utf8mb4,MySQL 8.0。

## 表分组总览

| 分组 | 表 |
|---|---|
| 租户 | tenant、tenant_user |
| 租户/项目与权限 | project、project_member、project_expert、project_skill、skill、permission_audit_log |
| 对话与消息 | project_conversation、project_message、project_conversation_expert_session |
| 事件与 SSE | project_event、conversation_event_sequence |
| 调度与执行 | coordinator_dispatch、coordinator_analysis、coordinator_plan、coordinator_task、coordinator_agent_run |
| 人类请求 | human_request |
| 产物 | project_artifact、project_artifact_lineage |
| 提示词 | prompt_template、prompt_execution |
| CLI 通道 | coordinator_cli_submission |

## 表间关系(核心链路)

```
project ─┬─ project_member / project_expert / project_skill / permission_audit_log
         └─ project_conversation ─┬─ project_message ─┬─ coordinator_dispatch
                                  │                   ├─ coordinator_plan ─┬─ coordinator_task ─┬─ project_artifact
                                  │                   │                    └─ human_request
                                  │                   ├─ coordinator_agent_run
                                  │                   ├─ coordinator_analysis ── human_request
                                  │                   └─ project_event(按 conversation_id + sequence)
                                  ├─ project_conversation_expert_session
                                  └─ conversation_event_sequence
project_artifact ── project_artifact_lineage(output ← input)
prompt_template ── prompt_execution
```

---

# 1. digital_team_permission_audit_log — 权限审计日志

记录项目权限相关的关键操作,用于审计追溯。只增不改。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(96) | 业务主键 | `"audit-" + UUID`(ProjectRepository.audit) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 `X-Tenant-Id` |
| project_id | VARCHAR(64) | 所属项目 | 请求路径参数 |
| actor_user_id | VARCHAR(64) | 操作人 | 请求头 `X-User-Id` |
| action | VARCHAR(64) | 动作码 | 代码常量:`PROJECT_CREATED`、`PROJECT_UPDATED`、`MEMBER_UPSERTED`、`MEMBER_REMOVED`、`EXPERT_UPSERTED`、`EXPERT_REMOVED`、`SKILL_UPSERTED`、`SKILL_REMOVED` |
| target_id | VARCHAR(128) | 操作对象 id | 被操作对象(项目/成员/专家)的 business_id |
| detail | VARCHAR(512) | 补充说明 | 代码拼装(如角色变更前后值) |
| created_at | TIMESTAMP | 操作时间 | 数据库默认 |

---

# 2. digital_team_project — 项目

租户下的项目,是整个系统多租户资源的根。状态机:`ACTIVE ⇄ ARCHIVED`。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(API 的 projectId) | `"project-" + UUID`(ProjectRepository.insertProject) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 `X-Tenant-Id` |
| name | VARCHAR(128) | 项目名 | 创建/更新请求体;租户内唯一 |
| description | VARCHAR(1024) | 项目描述 | 创建/更新请求体,可空 |
| coordinator_agent_id | VARCHAR(128) | 本项目指定的协调器 agent | 更新请求体;空 = 用全局配置的 coordinator |
| status | VARCHAR(16) | 状态 | `ACTIVE`/`ARCHIVED`(ProjectStatus 枚举),默认 ACTIVE |
| created_by | VARCHAR(64) | 创建人 | 请求头 `X-User-Id` |
| created_at / updated_at | TIMESTAMP | 时间戳 | 数据库默认 / ProjectService.updateProject 时 SQL 写 CURRENT_TIMESTAMP |

---

# 3. digital_team_project_member — 项目成员

项目成员与角色。角色决定 API 权限(ProjectService.requireRole)。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| project_id | VARCHAR(64) | 所属项目 | 请求路径参数(FK → project.business_id) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 |
| user_id | VARCHAR(64) | 成员用户 | 请求体/路径参数 |
| role | VARCHAR(16) | 角色 | `OWNER`/`MEMBER`/`VIEWER`(ProjectRole 枚举);(project_id, user_id) 唯一 |
| created_at / updated_at | TIMESTAMP | 时间戳 | 数据库默认 / 更新角色时 SQL 写入 |

---

# 4. digital_team_project_expert — 项目专家挂载

项目启用的专家及开关。专家本体来自代码内 ExpertRegistry(配置),此表只记录"项目是否启用某专家"。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| project_id | VARCHAR(64) | 所属项目 | 请求路径参数(FK → project) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 |
| expert_id | VARCHAR(128) | 专家 id | 请求体(如 `expert-analysis`);(project_id, expert_id) 唯一 |
| enabled | BOOLEAN | 是否启用 | 请求体,默认 TRUE;ExpertSelector 只选 enabled 的专家 |
| created_at / updated_at | TIMESTAMP | 时间戳 | 数据库默认 / 更新时 SQL 写入 |

---

# 5. digital_team_project_conversation — 会话任务(前端所说的 task)

用户在项目下创建的对话任务。一次进入聊天室 = 一个 conversation;后续所有消息、事件都挂在其下。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(API 的 taskId) | `"task-" + UUID`(ConversationTaskRepository.create) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 |
| project_id | VARCHAR(64) | 所属项目 | 请求路径参数(FK → project) |
| session_id | VARCHAR(128) | **对话业务会话号**(前端/AgentCore 的 businessSessionId) | `"session-" + UUID`(创建时生成) |
| coordinator_session_id | VARCHAR(128) | 协调器 AgentCore 会话号(跨消息复用) | 首次意图分析成功后 worker 写 `decision.getCoordinatorSessionId()`;同任务后续消息复用 |
| coordinator_agent_id | VARCHAR(128) | 实际使用的协调器 agent | worker 与 coordinator_session_id 同批写入 |
| title | VARCHAR(128) | 标题 | 创建请求体,可空 |
| status | VARCHAR(32) | 状态 | 默认 `ACTIVE`;目前仅创建/删除,无其他流转。删除为**级联删除**:按外键依赖顺序清理本会话关联的全部记录(消息、事件、计划、子任务、产物、人类请求、序列、CLI 载荷、提示词审计等),并调用 AgentCore `deleteSession` 删除协调器与各专家的会话历史(见 ProjectConversationMapper 的级联语句) |
| created_at | TIMESTAMP | 创建时间 | 数据库默认 |

---

# 6. digital_team_project_message — 用户消息

用户在对话里发的一条消息(只存用户侧文本;AI 回复在 project_event 里)。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(messageId) | `"message-" + UUID`(MessageEventRepository.insertMessage) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 |
| project_id | VARCHAR(64) | 项目 | 请求路径(FK → project) |
| conversation_id | VARCHAR(64) | 所属会话任务 | 请求路径(FK → project_conversation) |
| user_id | VARCHAR(64) | 发送人 | 请求头 `X-User-Id` |
| client_message_id | VARCHAR(128) | 客户端幂等键 | 请求体 `client_message_id`;(tenant, project, client_message_id) 唯一,重复提交返回首次结果 |
| message_text | TEXT | 消息文本 | 请求体 `text`;人类澄清时 appendMessageText 追加 `\nHuman clarification: ...` |
| attachment_refs | TEXT | 附件引用 JSON 数组 | 请求体 `attachment_refs`,JSON 序列化;存 artifact business_id |
| status | VARCHAR(32) | 状态 | 恒为 `ACCEPTED`(受理即写入) |
| created_at | TIMESTAMP | 发送时间 | 数据库默认 |

---

# 7. digital_team_project_event — 面向前端的事件流(持久化)

SSE 事件的可重放事实源。客户端断线重连用 `Last-Event-ID`(= 本表 sequence)从这里补发。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键 | `"event-" + UUID`(MessageEventRepository.insertEvent) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 |
| project_id | VARCHAR(64) | 项目 | (FK → project) |
| conversation_id | VARCHAR(64) | 所属会话任务 | (FK → project_conversation) |
| message_id | VARCHAR(64) | 所属消息 | 当前消息 business_id,可空 |
| sequence | BIGINT | 会话内单调事件序号(SSE 的 id) | `allocateSequence`:conversation_event_sequence 上 CAS 分配;(tenant, conversation_id, sequence) 唯一 |
| event_type | VARCHAR(64) | 事件类型 | `MESSAGE_ACCEPTED_INTERNAL`(内部受理标记)/ `COORDINATOR_ANALYZING`(Coordinator 生成的 AgentEvent 型事件)/ `AGENT_RUN_MARKER`(AgentCore 重放标记) |
| visibility | VARCHAR(16) | 可见性 | `PUBLIC`(SSE 下发)/ `INTERNAL`(仅受理标记) |
| payload | TEXT | 载荷 JSON | AgentEvent 序列化(coordinatorPhase/coordinatorChat/userMessage 等),或 MARKER 载荷 `{sessionId, expertId, startSequence}` |
| created_at | TIMESTAMP | 落库时间 | 数据库默认 |

**写入方**:`CoordinatorMessageService.accept`(userMessage、analyzing、受理标记)、
`SingleExpertWorker.publishAgentEvent`(coordinatorPhase/coordinatorChat/planUpdate/newPlanStep 等)、
`insertCoordinatorMarker` / `startTask`(AGENT_RUN_MARKER)。
**读取方**:`ProjectEventStreamHub.subscribe`(重放)、`pollDatabaseEvents`(跨实例轮询)、`WorkspaceService.findEvents`。

---

# 8. digital_team_conversation_event_sequence — 会话事件序列号

每个会话一个序列分配器,为 project_event.sequence 提供**原子递增**(CAS 循环,失败重试)。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| tenant_id | VARCHAR(64) | 租户 | 请求头 |
| conversation_id | VARCHAR(64) | 会话任务 | (FK → project_conversation);(tenant, conversation_id) 唯一 |
| next_sequence | BIGINT | 下一个可用序列 | 首条消息时 `insertSequence` 置 2(1 留给首事件);之后 `updateSequence` 以 `next = old, newNext = old+1` 条件更新实现 CAS |

---

# 9. digital_team_coordinator_dispatch — 执行票(worker 调度单元)

每条用户消息生成一张执行票,`SingleExpertWorker.runOnce()` 每 500ms 领取处理。
一张票 = 一次意图分析 + 可能的计划与专家执行,直到终态。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(dispatchId) | `"dispatch-" + UUID`(MessageEventRepository.insertDispatch) |
| tenant_id / project_id | VARCHAR(64) | 归属 | 请求上下文 |
| conversation_id | VARCHAR(64) | 所属会话 | 请求路径 |
| message_id | VARCHAR(64) | 关联消息 | 消息 business_id(FK → project_message);每消息一张票 |
| status | VARCHAR(16) | 状态 | `PENDING`(受理)→ `RUNNING`(worker 领取)→ `COMPLETED`/`FAILED`/`WAITING_HUMAN`/`CANCELLED` |
| attempt_count | INT | 尝试次数 | worker claim 时 +1 |
| available_at | TIMESTAMP | 最早可领取时间 | 默认创建时间;HITL 恢复时 `resetDispatchPending` 置为当前时间 |
| lease_owner | VARCHAR(128) | 租约持有者 | worker 实例 id `"coordinator-" + UUID`(claim 写入,release/complete 清空) |
| lease_expires_at | TIMESTAMP | 租约到期 | claim/renew 写 `now + 30s`;过期即视为可被他人领取 |
| last_error | VARCHAR(1024) | 最后错误 | worker 失败时 completeDispatch 写入 |
| created_at / updated_at | TIMESTAMP | 时间戳 | 数据库默认 / 各状态迁移 SQL 更新 |

---

# 10. digital_team_coordinator_analysis — 意图分析记录

每次协调器意图分析的落档(输入快照、决策、模型与 schema 版本),审计与排查用。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(analysisId) | `"analysis-" + UUID`(IntentAnalysisRepository.insertAnalysis) |
| tenant_id / project_id | VARCHAR(64) | 归属 | 请求上下文 |
| user_id | VARCHAR(64) | 触发用户 | 请求头 |
| input_snapshot | TEXT | 输入上下文 JSON | `IntentAnalysisContext` 序列化(项目信息、文本、附件、pending 状态) |
| model_name | VARCHAR(128) | 模型/代理标识 | `"agentcore:" + COORDINATOR_AGENT_ID` |
| prompt_version | VARCHAR(32) | 提示词版本标识 | 常量 `"database-managed"` |
| schema_version | VARCHAR(32) | 决策 schema 版本 | 常量 `"task-intent-v1"` |
| decision_type | VARCHAR(32) | 决策类型 | `ANSWER` / `ASK_HUMAN` / `CREATE_PLAN`(DecisionType) |
| decision_json | TEXT | 决策 JSON | CLI 提交的决策载荷(见 coordinator_cli_submission) |
| repaired | BOOLEAN | 是否修复过 | 修复成功后重跑 analyze 时置 TRUE |
| created_at | TIMESTAMP | 时间 | 数据库默认 |

---

# 11. digital_team_coordinator_plan — 执行计划

一次 CREATE_PLAN 决策生成一张执行计划,内含若干子任务。修复(repair)生成新版本计划并替代旧版。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(planId) | `"plan-" + UUID`(ExecutionRepository.createPlan/createReplan) |
| tenant_id / project_id | VARCHAR(64) | 归属 | 消息上下文 |
| conversation_id | VARCHAR(64) | 所属会话 | 消息上下文 |
| message_id | VARCHAR(64) | 关联消息 | (FK → project_message) |
| analysis_id | VARCHAR(64) | 关联分析 | decision.getAnalysisId() |
| status | VARCHAR(32) | 状态 | `RUNNING` → `SUCCEEDED`/`FAILED`/`CANCELLED`;被新版本替代时 `SUPERSEDED` |
| plan_version | INT | 版本号 | 默认 1;修复 +1;(tenant, project, message_id, plan_version) 唯一 |
| intent_json | TEXT | 任务意图 JSON | CoordinatorDecision.taskIntent 序列化 |
| plan_json | TEXT | 计划 JSON | PlanningResult.rawJson(模型/CLI 输出的原始计划) |
| repair_count | INT | 修复次数 | 每次修复 +1 |
| supersedes_plan_id | VARCHAR(64) | 替代哪个旧版计划 | 修复时写上一版 planId |
| created_at / updated_at | TIMESTAMP | 时间戳 | 数据库默认 / 状态迁移 SQL |

---

# 12. digital_team_coordinator_task — 协调子任务(专家执行单元)

计划里的每个执行步骤。一个子任务 = 一次专家 AgentCore run。
状态机:`PENDING → STARTING → RUNNING → SUCCEEDED / FAILED / CANCELLED / TIMED_OUT`;中途可
`WAITING_HUMAN`(专家求助)/`CORRECTING`(语义评审拒绝后自动纠正)。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(taskId) | `"task-" + UUID`(ExecutionRepository.insertTask 等) |
| tenant_id / project_id | VARCHAR(64) | 归属 | 消息上下文 |
| plan_id | VARCHAR(64) | 所属计划 | (FK → coordinator_plan) |
| task_key | VARCHAR(128) | 计划内任务键 | 计划 JSON 的 taskKey(如 `analyze`、`write-report`);(plan_id, task_key) 唯一 |
| request_id | VARCHAR(128) | 幂等请求号 | `messageId + ":" + taskKey`(纠正任务加 `:correction-1`,复用任务加 `:v2:`);(tenant, request_id) 唯一 |
| expert_id | VARCHAR(128) | 执行专家 | 创建时置空,`assignExpert` 时写入(ExpertSelector 按 required_capabilities 选择) |
| session_id | VARCHAR(128) | 专家 AgentCore 会话 | `saveSession` 写入;复用场景沿用上一消息的 session |
| status | VARCHAR(32) | 状态 | 见上方状态机 |
| objective | TEXT | 任务目标(给专家的任务文本) | 计划 JSON 的 objective |
| attachment_refs | TEXT | 输入附件引用 JSON | 消息 attachment_refs 序列化 |
| result_json | TEXT | 专家结果 JSON | CLI `submit-result` 写入(如 `{"content":"..."}`);纠正接受时 acceptCorrection 写入 |
| last_sequence | BIGINT | AgentCore 事件游标 | `advanceRunningTask`/`advanceTask` 随事件推进;**会话复用水位**(findMaxLastSequenceBySession)依据 |
| lease_owner / lease_expires_at | VARCHAR(128)/TIMESTAMP | 任务租约(历史字段) | 早期版本使用,当前调度以 dispatch 租约为主,基本不再读写 |
| dependencies | TEXT | 依赖任务键 JSON 数组 | 计划 JSON 的 dependencies;worker 按依赖就绪启动 |
| required_capabilities | TEXT | 所需能力 JSON 数组 | 计划 JSON;ExpertSelector 依此选专家 |
| expected_output | VARCHAR(512) | 期望输出 | 计划 JSON |
| acceptance_criteria | VARCHAR(1024) | 验收标准 | 计划 JSON;纠正任务追加失败原因 |
| correction_of | VARCHAR(64) | 纠正的对象任务 | 纠正任务写原任务 business_id |
| correction_count | INT | 纠正次数 | markCorrection 置 1 |
| result_accepted | BOOLEAN | 结果是否通过语义评审 | CLI 提交结果置 TRUE;被拒纠正置 FALSE |
| reused_from_task_id | VARCHAR(64) | 复用的源任务 | 修复计划中复用上一版成功任务时写入 |
| consecutive_failures | INT | AgentCore 连续失败计数 | worker 每次流失败 +1;阈值(默认 3)后任务判 FAILED |
| created_at / updated_at | TIMESTAMP | 时间戳 | 数据库默认 / 状态迁移 SQL |

---

# 13. digital_team_human_request — 人类请求(协调者提问/专家求助共用)

HITL 的统一落点。协调器 ASK_HUMAN 与专家 confirm 都建行,前端答题后 resolve。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(humanRequestId) | `"human-" + UUID`(HumanRequestRepository) |
| analysis_id | VARCHAR(64) | 关联分析(协调者提问) | decision 的 analysisId(FK → coordinator_analysis) |
| task_id | VARCHAR(64) | 关联子任务(专家求助) | CLI ask-human 的子任务 id(FK → coordinator_task) |
| message_id | VARCHAR(64) | 关联消息 | linkDispatch 时写入(该请求被哪条消息的 dispatch 承接) |
| dispatch_id | VARCHAR(64) | 关联执行票 | linkDispatch 时写入 |
| tenant_id / project_id | VARCHAR(64) | 归属 | 请求上下文 |
| request_type | VARCHAR(32) | 请求类型 | `CLARIFICATION`/`APPROVAL`(HumanRequestType;当前业务只产生 CLARIFICATION) |
| question | TEXT | 问题文本 | 协调者决策 / 专家的 confirm 问题 |
| agent_question_id | VARCHAR(128) | AgentCore 侧问题 id | 专家 confirm 事件的 questionId,恢复(恢复 resume)时回传 |
| allowed_roles | VARCHAR(128) | 允许回答的角色 | 默认 `'OWNER,MEMBER'` |
| input_schema | TEXT | 回答的 JSON Schema | 代码常量(如 `{"answer": string}`) |
| status | VARCHAR(32) | 状态 | `PENDING` → `RESOLVED`(已回答)/ `EXPIRED`(超时) |
| decision | VARCHAR(32) | 回答判定 | `ANSWER`/`APPROVE`/`REJECT`(HumanDecision) |
| response_json | TEXT | 回答内容 JSON | 前端答题请求体序列化 |
| response_idempotency_key | VARCHAR(128) | 回答幂等键 | 请求头;(tenant, key) 唯一,重复回答返回首次结果 |
| responded_by | VARCHAR(64) | 回答人 | 请求头 `X-User-Id` |
| expires_at | TIMESTAMP | 过期时间 | 创建时 `now + 24h` |
| created_at / resolved_at | TIMESTAMP | 时间 | 数据库默认 / resolve、expire 时写入 |

---

# 14. digital_team_project_artifact — 产物

文件产物(专家产出、用户上传)的元数据;实际字节在对象存储(MinIO)。状态机:`UPLOADING → AVAILABLE`。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键(artifactId) | `"artifact-" + UUID`(reserve 时生成) |
| tenant_id / project_id | VARCHAR(64) | 归属 | 请求上下文(FK → project) |
| task_id | VARCHAR(64) | 产出该文件的子任务 | 专家/CLI 上传时写(FK → coordinator_task);用户手动上传为空 |
| expert_run_id | VARCHAR(128) | 专家 AgentCore 会话 | 专家上传时写 task.sessionId |
| version | INT | 版本 | `selectNextVersion`:(project_id, file_name) 下 MAX(version)+1 |
| storage_key | VARCHAR(128) | 对象存储 key(SSE 附件 path) | `FileStore.reserve` 生成(`file-<uuid>`);全局唯一 |
| file_name | VARCHAR(255) | 文件名 | 上传方指定 |
| media_type | VARCHAR(128) | MIME 类型 | 上传方指定(text/plain、text/markdown…) |
| size_bytes | BIGINT | 大小 | complete 时由文件内容长度写入 |
| sha256 | VARCHAR(64) | 内容校验和 | complete 时计算写入 |
| status | VARCHAR(32) | 状态 | reserve 置 `UPLOADING`;complete 置 `AVAILABLE` |
| created_by | VARCHAR(64) | 创建者 | 用户手动 = `X-User-Id`;专家上传 = `"expert:" + expertId` |
| created_at / completed_at | TIMESTAMP | 时间 | 数据库默认 / complete 时写入 |

**读取方**:`GET /artifacts/{artifactId}`(元数据+临时下载 URL)、
`GET /artifacts/by-storage/{storageKey}`(SSE 附件 path 反查)、
`findAvailableStorageKeys`(下游任务取上游产物)、Workspace 快照。

---

# 15. digital_team_project_artifact_lineage — 产物血缘

记录"某子任务的产物依赖了哪些上游任务的产物",构成计划内的产物依赖图。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| output_artifact_id | VARCHAR(64) | 下游产物(消费者) | 上传方当前任务的 artifact business_id(FK → project_artifact) |
| input_artifact_id | VARCHAR(64) | 上游产物(依赖) | 按 task.dependencies 的任务键在 `project_artifact` 中查找出的上游 artifact id |
| created_at | TIMESTAMP | 时间 | 数据库默认 |

**写入方**:`ArtifactService.registerExpertArtifact` / `CliSubmissionService.uploadArtifact`
(两者都在上传成功后按 `dependencies` 任务键批量补写);(output, input) 唯一。

---

# 16. digital_team_coordinator_agent_run — 协调器 agent run 记录

协调器每次意图分析的 AgentCore run 状态落档(幂等 create-or-load,run_key 唯一)。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键 | `"coordinator-run-" + UUID` |
| tenant_id / project_id | VARCHAR(64) | 归属 | 请求上下文 |
| message_id | VARCHAR(64) | 关联消息 | 消息 business_id(可空) |
| run_key | VARCHAR(128) | 幂等键 | `"message-" + messageId`(每消息一个 ANALYZE run);(tenant, run_key) 唯一 |
| session_id | VARCHAR(128) | AgentCore 会话 | submit 后 saveSession 写入;后续消息复用该会话 |
| business_session_id | VARCHAR(128) | 对话业务会话 | conversation.session_id |
| stage | VARCHAR(16) | 阶段 | `ANALYZE`(首轮)/ `REPAIR`(修复轮);修复在同一个 run 上继续 |
| status | VARCHAR(32) | 状态 | `PENDING` → `RUNNING` → `SUCCEEDED`/`FAILED` |
| last_sequence | BIGINT | 事件游标(会话水位) | advance/complete 随事件推进;findLastSequenceBySessionExcluding 依据 |
| context_json | TEXT | 输入上下文 | IntentAnalysisContext 序列化 |
| invalid_output | TEXT | 上次非法输出 | prepareRepair 写入,修复时注入提示词 |
| output_json | TEXT | 最终决策 JSON | complete 时写 CLI 决策载荷 |
| created_at / updated_at | TIMESTAMP | 时间 | 数据库默认 / 状态迁移 SQL |

---

# 17. digital_team_prompt_template — 提示词模板(版本化)

数据库管理的提示词模板。状态机:`DRAFT → PUBLISHED → RETIRED`。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键 | `"prompt-" + UUID`(PromptService.create) |
| prompt_key | VARCHAR(128) | 提示词键 | 创建请求体(如 `coordinator.execution`);(prompt_key, version) 唯一 |
| agent_scope | VARCHAR(128) | 适用 agent 范围 | 创建请求体 |
| scene | VARCHAR(64) | 场景 | 创建请求体(如 `COORDINATOR_EXECUTION`) |
| version | INT | 版本 | selectNextVersion:同 prompt_key 下 MAX(version)+1 |
| status | VARCHAR(16) | 状态 | 创建 `DRAFT`;publish 置 `PUBLISHED`(旧版置 `RETIRED`) |
| template_content | TEXT | 模板正文 | 创建请求体;含 `{{变量}}` 占位符,渲染见 prompt_execution |
| variables_schema | TEXT | 变量 JSON Schema | 创建请求体 |
| created_by | VARCHAR(64) | 创建者 | 请求头 `X-User-Id` |
| created_at / published_at | TIMESTAMP | 时间 | 数据库默认 / publish 时写入 |

**种子数据**:`db/init/02-seed.sql` 内置 skill 与多套 coordinator 提示词模板(含
`{{output_schema}}` 占位符,运行时由 OutputSchemaProvider 注入决策 JSON Schema)。

---

# 18. digital_team_prompt_execution — 提示词执行审计

每次实际渲染提示词的快照(模板 id/版本、渲染结果、变量),可追溯与回放。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键 | `"prompt-exec-" + UUID`(PromptRepository.audit) |
| tenant_id / project_id | VARCHAR(64) | 归属 | 调用上下文 |
| conversation_id | VARCHAR(64) | 会话 | 调用上下文,可空 |
| invocation_id | VARCHAR(128) | 调用标识 | `runId + ":" + stage`(协调器侧);(invocation_id, scene) 唯一 |
| agent_id | VARCHAR(128) | 目标 agent | effectiveCoordinatorAgent |
| scene | VARCHAR(64) | 场景 | 与模板 scene 一致 |
| prompt_template_id | VARCHAR(64) | 所用模板 | 渲染命中的模板 business_id(FK → prompt_template) |
| prompt_version | INT | 所用模板版本 | 同上 |
| rendered_prompt | TEXT | 渲染后的完整提示词 | PromptService.render 输出 |
| variables_snapshot | TEXT | 变量快照 JSON | 渲染时传入的 variables + context |
| created_at | TIMESTAMP | 时间 | 数据库默认 |

---

# 19. digital_team_project_conversation_expert_session — 会话-专家 session 复用映射

记录"该对话中某专家最近一次使用的 AgentCore 会话",供下一条消息的同类任务**复用会话保持上下文连续**。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | VARCHAR(64) PK | 业务主键(无自增) | `"exp-session-" + UUID`(ExecutionRepository.saveExpertSession) |
| tenant_id / project_id | VARCHAR(64) | 归属 | 调用上下文 |
| conversation_id | VARCHAR(64) | 对话任务 | 调用上下文 |
| expert_id | VARCHAR(128) | 专家 | 任务 expertId;(conversation_id, expert_id) 唯一 |
| session_id | VARCHAR(128) | 专家 AgentCore 会话 | startTask 后 upsert;下次同对话同专家复用 |
| message_id | VARCHAR(64) | 最近使用该会话的消息 | upsert 时写当前 messageId;查询时排除当前消息避免同计划内并行任务共享会话 |
| created_at | TIMESTAMP | 时间 | 数据库默认 |

---

# 20. digital_team_skill — 技能目录

平台级技能定义(名称、描述、提示词)。项目通过 project_skill 挂载启用。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键 | **种子数据**(02-seed.sql 内置);暂无运行时创建 |
| name | VARCHAR(128) | 技能名 | 种子数据 |
| description | VARCHAR(1024) | 描述 | 种子数据 |
| prompt | TEXT | 技能提示词 | 种子数据 |
| created_at / updated_at | TIMESTAMP | 时间 | 数据库默认 |

---

# 21. digital_team_project_skill — 项目技能挂载

项目对技能的启用/禁用关系(与 project_expert 同构)。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| project_id | VARCHAR(64) PK | 项目 | 请求路径(FK → project) |
| tenant_id | VARCHAR(64) | 租户 | 请求头 |
| skill_id | VARCHAR(64) PK | 技能 | 请求体(FK → skill);(project_id, skill_id) 复合主键 |
| enabled | BOOLEAN | 是否启用 | 请求体,默认 TRUE |
| created_at / updated_at | TIMESTAMP | 时间 | 数据库默认 / 更新时 SQL 写入 |

---

# 22. digital_team_coordinator_cli_submission — CLI 提交载荷(单次消费)

tc CLI 与 AgentCore 侧向 Coordinator 提交结构化结果的**暂存通道**:
决策/计划/评审意见先落到这里,worker 按 `(task_id, kind)` 取出后**即删**——保证单次消费,不会串到下一轮消息。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键 | UUID(插入时生成) |
| task_id | VARCHAR(128) | 所属对话任务(键空间) | 提交接口路径的 conversation taskId |
| kind | VARCHAR(16) | 载荷类型 | `DECISION`(意图决策)/ `PLAN`(执行计划)/ `VERDICT`(语义评审意见) |
| payload | TEXT | 载荷 JSON | 提交接口请求体原样保存 |
| created_at | TIMESTAMP | 时间 | 数据库默认 |

**写入方**:`CliSubmissionService.submitDecision/submitPlan/submitVerdict`(幂等 replace)。
**消费方**:`SingleExpertWorker.process`(decision/plan)、语义评审服务(verdict)——消费后 `delete`。

---

# 23. digital_team_tenant — 租户(多租户根表)

多租户隔离的根实体。所有租户域表的 `tenant_id` 均引用本表 `business_id`;
访问门禁(`HeaderIdentityProvider`)要求租户存在且状态为 ACTIVE。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| business_id | VARCHAR(64) | 业务主键,全库引用 | `"tenant-" + UUID`(TenantServiceImpl.createTenant) |
| name | VARCHAR(128) | 租户名称(全局唯一) | 平台管理员创建请求 |
| description | VARCHAR(512) | 租户描述 | 平台管理员创建/更新请求,可空 |
| owner_user_id | VARCHAR(64) | 负责人(外部 userId) | 创建请求;仅平台管理员可改;负责人不可被移除成员 |
| status | VARCHAR(16) | 状态 | `ACTIVE` / `DISABLED`(禁用后全租户访问 403) |
| created_by | VARCHAR(64) | 创建者 userId | 创建请求的 X-User-Id |
| created_at / updated_at | TIMESTAMP | 时间 | 数据库默认 / 业务 SQL 显式更新 |

**写入方**:`TenantServiceImpl`(平台管理员建/改/禁;硬删除仅限无项目时)。
**读取方**:`HeaderIdentityProvider`(门禁)、`TenantServiceImpl`(列表/详情)。

# 24. digital_team_tenant_user — 租户成员(租户 → 外部 userId 赋权)

服务不建用户表:userId 来自外部登录系统,本表仅保存赋权关系。
门禁要求当前用户必须存在本表记录,否则 403 `TENANT_ACCESS_FORBIDDEN`。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| tenant_id | VARCHAR(64) | 所属租户 | (FK → tenant.business_id) |
| user_id | VARCHAR(64) | 外部用户标识 | 赋权接口请求的 userId |
| role | VARCHAR(16) | 角色 | `TENANT_ADMIN`(可管本租户成员/信息)/ `MEMBER`(正常使用) |
| created_at / updated_at | TIMESTAMP | 时间 | 数据库默认 / 业务 SQL 显式更新 |

**写入方**:`TenantServiceImpl.assignMember`(平台管理员或租户管理员;创建租户时
负责人自动成为 TENANT_ADMIN;最后一名 TENANT_ADMIN 不可移除/降级)。
**读取方**:`HeaderIdentityProvider`(成员门禁)、`TenantServiceImpl.listMyTenants/listMembers`。

---

# 25. digital_team_platform_admin — 平台管理员

平台级超级管理员列表。**赋权方式:直接向本表插入 user_id 行**(无管理 API,
运维可操作)。运行时判定 = 本表 ∪ `PLATFORM_ADMIN_USERS` 环境变量(后者
保留作引导兜底)。平台管理员豁免租户成员门禁(可访问任意 ACTIVE 租户,
禁用/未知租户仍拒绝),并拥有 `/api/v1/admin/tenants` 全部管理权限。

| 字段 | 类型 | 含义/用途 | 生成来源 |
|---|---|---|---|
| id | BIGINT PK | 代理主键 | 数据库自增 |
| user_id | VARCHAR(64) | 外部用户标识 | 运维手工 INSERT(唯一) |
| created_at | TIMESTAMP | 赋权时间 | 数据库默认 |

**写入方**:运维手工 INSERT(如 `INSERT INTO digital_team_platform_admin (user_id) VALUES ('ops-admin');`)。
**读取方**:`HeaderIdentityProvider.isPlatformAdmin`、`TenantServiceImpl.isPlatformAdmin`。
