# TeamCoordinator 架构设计文档

> 版本: v1.2 | 更新: 2026-08-05 | 基于 MVP 实现现状

## 1. 概述

TeamCoordinator 是一个 AI Agent 编排服务。接收用户自然语言消息，通过 Coordinator Agent 分析意图，创建执行计划，将任务分派给专家 Agent，流式返回结果。

### 核心闭环

```
用户消息 → 意图分析 → 计划生成 → 专家调度 → AgentCore 执行 → SSE 事件推送 → 最终回复
```

### 技术栈

- **Java 21** + **Spring Boot 2.7**
- **MyBatis**（无 JPA/Hibernate）→ MySQL
- **Flyway** 数据库迁移
- **SSE** 事件推送（`ProjectEventStreamHub`）
- **AgentCore** HTTP + SSE 适配器（`HttpAgentCoreAdapter` / `MockAgentCoreAdapter`）

## 2. 包结构

```
org.cmb.teamcoordinator
├── api/            REST 控制器
├── agentcore/      AgentCore 抽象层（适配器、事件模型、请求/响应）
├── artifact/       文件存储（MinIO / Mock）和产物管理
├── common/         异常处理、Feature Flag、Trace
├── config/         配置属性（DigitalTeamProperties）
├── coordinator/    消息入口、对话任务、SSE 事件流 Hub
├── execution/      任务调度、Worker 主循环、Dispatch 租约
├── human/          Human-in-the-loop（人工请求与响应）
├── intent/         意图分析、Coordinator Agent 客户端、决策模型
├── persistence/    MyBatis 执行器和行映射
├── planning/       任务计划生成、校验、专家选择
├── project/        项目、成员、权限、身份
└── prompt/         Prompt 模板管理与渲染
```

## 3. 核心流程

### 3.1 消息提交

```
POST /api/v1/projects/{projectId}/tasks/{taskId}/messages
  → CoordinatorMessageService.accept()
    → INSERT project_message
    → INSERT project_event (userMessage)      ← 谁发了什么
    → INSERT project_event (coordinatorPhase: analyzing)
    → INSERT coordinator_dispatch (PENDING)
    → streamHub.publish()                     ← 实时推 SSE
    → 202 Accepted
```

### 3.2 Worker 调度

```
SingleExpertWorker.runOnce()  ← @Scheduled(500ms)
  → ExecutionRepository.claimNext()
    → SELECT dispatch WHERE lease 过期 + 同对话串行
    → UPDATE lease_owner + lease_expires_at   ← DB 租约锁
  → process(work)
```

`claimNext()` 四个条件：
1. 状态 PENDING 或 RUNNING
2. 已到可处理时间
3. 租约已过期（故障接管）
4. 同 conversation 没有更早的未完成 dispatch（保证顺序）

### 3.3 意图分析

```
process()
  → IntentAnalysisService.analyzeForDispatch()
    → CoordinatorAgentClient.execute()
      → agentCore.submitRun("coordinator", request)    ← 提交到 AgentCore
      → agentCore.streamEvents(sessionId, afterSequence) ← 拉 SSE chunk
      → eventSink → streamHub.publish()                ← 实时推送给前端
      → 遇到 "end" → 解析 CoordinatorDecision

决策分支:
  ANSWER     → emitPhase(answering) → chat → emitPhase(completed)
  ASK_HUMAN  → emitPhase(waiting) → confirm → 等待人工回答
  CREATE_PLAN → 进入计划生成
```

### 3.4 计划生成与执行

```
process()
  → emitPhase(planning)
  → PlanningService.createPlan()  → 通过 AI Model 生成 CoordinatorPlanSpec
    → PlanValidator 校验（DAG环、能力存在性、最多8任务、依赖深度≤2）
    → 最多2次修复
  → ExecutionRepository.createPlan()  → INSERT plan + tasks

advancePlan()
  → 依赖满足的 PENDING task → assignExpert → startTask()
    → agentCore.submitRun(expertId, request)  → 返回 sessionId
    → INSERT AGENT_RUN_MARKER                  ← 回放标记
    → streamHub.publish(newPlanStep)           ← 实时推

  → RUNNING task → consumeEvents()
    → agentCore.streamEvents(sessionId, lastSequence)
    → publishAgentEventLive()                  ← 实时推，不落库
    → 终端事件 → advanceTask(SUCCEEDED/FAILED/...)

  → 全部 SUCCEEDED → emitPhase(answering) → chat → emitPhase(completed)
  → 有 FAILED → emitPhase(failed) → 完成或纠正
```

### 3.5 人工介入

两条路径，阶段不同：

**Coordinator 层 ASK_HUMAN**（意图分析阶段，计划创建前）：

```
Coordinator Agent 返回 {"decision_type":"ASK_HUMAN","question":"请补充..."}
  → process() 检测 DecisionType.ASK_HUMAN
  → humanRequests.linkDispatch()          ← 关联 dispatch 和 human_request
  → publishAgentEvent(confirm)            ← 发 SSE 告诉前端
  → completeDispatch("WAITING_HUMAN")     ← dispatch 不再被领取

用户回答 → HumanRequestService.respond()
  → taskId == null（Coordinator 层没有 task）
  → resumeCoordinatorDispatch()           ← dispatch 重置 PENDING
  → Worker 重新领取 → process() 重新意图分析（带用户补充信息）
```

**Expert 层 confirm**（任务执行中，AgentCore 反问）：

```
AgentCore streamEvents 返回 "confirm" chunk
  → consumeEvents() → applyEvent()
  → advanceTask(taskId, "WAITING_HUMAN")  ← 单个任务暂停
  → humanRequests.createExpertClarification()
  → 不阻塞并行任务，阻塞依赖当前 task 的下游

用户回答 → HumanRequestService.respond()
  → taskId != null（Expert 层有 task）
  → agentCore.answerQuestion(sessionId, questionId, answers)
  → 或 submitRun() 复用同一 sessionId
  → resumeTask() → dispatch 重新入队 → Worker 继续 consumeEvents()
```

## 4. 数据库核心表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `project` | 项目 | `business_id`, `tenant_id`, `status` |
| `project_member` | 项目成员 | `user_id`, `role` (OWNER/ADMIN/MEMBER/VIEWER) |
| `project_expert` | 项目专家 | `expert_id`, `enabled` |
| `project_conversation` | 对话 Task | `business_id`, `session_id` (业务标识) |
| `project_message` | 用户消息 | `user_id`, `message_text`, `client_message_id` (幂等) |
| `project_event` | SSE 事件 | `sequence`, `event_type`, `payload` (JSON) |
| `coordinator_dispatch` | 调度队列 | `status`, `lease_owner`, `lease_expires_at` |
| `coordinator_agent_run` | Coordinator Agent 调用 | `run_key` (幂等), `session_id`, `stage`, `last_sequence` |
| `coordinator_plan` | 执行计划 | `plan_version`, `intent_json`, `plan_json` |
| `coordinator_task` | 专家任务 | `task_key`, `expert_id`, `session_id`, `status`, `dependencies` |
| `human_request` | 人工请求 | `type` (CLARIFICATION/APPROVAL), `status`, `allowed_roles` |
| `project_artifact` | 产物 | `storage_key`, `version`, `status` |
| `project_conversation_expert_session` | Expert session 映射 | `conversation_id`, `expert_id`, `session_id` |

## 5. Session ID 模型

三个层级的 sessionId，互不干扰：

| 层级 | 存储位置 | 生成方 | 用途 |
|---|---|---|---|
| Task | `project_conversation.session_id` | Coordinator (`"session-" + UUID`) | 业务标识，作为 `X-Session-Id` 头传给 AgentCore |
| Coordinator Agent | `coordinator_agent_run.session_id` | AgentCore `submitRun` 返回 | 查询/取消 Coordinator Agent run；repair 复用 |
| Expert Agent | `coordinator_task.session_id` | AgentCore `submitRun` 返回 | 查询/取消/恢复 Expert Agent run；human resume 复用 |

原则：同一 Task 内，同一 agent 多次调用复用同一个 sessionId（第一次 `submitRun` 返回的那个）。

### 5.1 Coordinator Session 跨消息复用

Task 内第一条消息 → AgentCore 创建新的 Coordinator session → 存入 `project_conversation.coordinator_session_id`。后续消息的意图分析传入该 `conversationSessionId`，AgentCore 在同一会话内继续对话，保持上下文连续。

```
消息1 → Coordinator submitRun() {conversationSessionId: null}
        → AgentCore 返回 sessionId="coord-abc"
        → 存入 project_conversation.coordinator_session_id
        → 存入 coordinator_agent_run.session_id

消息2 → 读取 coordinator_session_id="coord-abc"
        → Coordinator submitRun() {conversationSessionId: "coord-abc"}
        → AgentCore 在同一会话内继续，上下文连续
```

### 5.2 Expert Session 跨消息复用

每个 Task 内，同一 Expert 首次调用创建新 session。Task 成功后，存入 `project_conversation_expert_session`。后续消息中同 Expert 的调用复用该 session。

```
消息1 → Expert-1 submitRun() → sessionId="exp-xyz"
        → Task SUCCEEDED → 存入 project_conversation_expert_session

消息2 → 读取 expert session → submitRun() {conversationSessionId: "exp-xyz"}
        → AgentCore 在同一会话内继续
```

注意：同一 Plan 内的并行 Task 不会共享 Expert session（session 仅在 Task SUCCEEDED 后保存，避免并行 Task 相互污染）。

### 5.3 三级 Session 总览

| 层级 | 存储表 | 复用时机 |
|---|---|---|
| Task business session | `project_conversation.session_id` | Task 创建时生成，所有消息共享 |
| Coordinator Agent session | `project_conversation.coordinator_session_id` | 首次意图分析后保存，后续消息复用 |
| Expert Agent session | `project_conversation_expert_session(expert_id)` | 首次 Task SUCCEEDED 后保存，后续消息复用 |

## 6. 事件模型

### 6.1 统一格式

所有 SSE 事件使用 `AgentEvent` 扁平 JSON，event name = `type` 字段：

```
id: {sequence}
event: {agentEvent.type}
data: {AgentEvent JSON}
```

### 6.2 事件类型

**Coordinator 生成**（落 `project_event`，用于回放）：

| type | 触发时机 |
|---|---|
| `userMessage` | 用户发送消息 |
| `coordinatorPhase` | 协调器阶段转换（analyzing/planning/dispatching/answering/waiting_human/completed/failed） |
| `planUpdate` | 执行计划创建 |
| `newPlanStep` | 专家任务开始 |

**Agent 生成**（不落库，只有 AGENT_RUN_MARKER 标记；回放时从 AgentCore 现场查询）：

| type | 含义 |
|---|---|
| `liveStatus` | 状态更新（初始化、思考中...） |
| `thinkingStart` / `thinkingDelta` / `thinkingEnd` | 思考过程 |
| `streamStart` / `textDelta` / `streamEnd` | 流式文本输出 |
| `chat` | 回复消息（含 content, attachments, usage） |
| `end` | 执行结束 |
| `error` | 执行失败 |
| `confirm` | 需要人工确认 |
| `planUpdate` / `newPlanStep` | 计划和步骤（专家内部） |
| `toolUsed` / `toolResult` | 工具调用 |
| `subagent*` | 子 Agent 事件 |
| `file` / `directory` / `streamingFile` | 文件操作 |

### 6.3 持久化策略

| 事件来源 | 落 `project_event`？ | 回放方式 |
|---|---|---|
| Coordinator 事件 | 是 | 从 DB 直接回放 |
| Agent 事件 | 否 | 插入 `AGENT_RUN_MARKER`（含 sessionId），回放时调 `agentCore.streamEvents(sessionId, 0)` |

### 6.5 跨实例事件投递

Coordinator 多实例部署时，SSE 连接可能与 Worker 不在同一实例：

| 路径 | 延迟 | 覆盖事件 |
|---|---|---|
| **内存直推** (`streamHub.publish()`) | 0ms | 同实例 subscriber 收到全部事件（Coordinator + Agent） |
| **DB 轮询** (`pollDatabaseEvents()`, 500ms) | ~500ms | 跨实例 subscriber 收到 Coordinator 事件 + `AGENT_RUN_MARKER` 触发 AgentCore 现场拉取 agent 事件 |

同实例和跨实例**同时跑**，靠 `send()` 里的 `event.sequence <= subscriber.lastSequence` 去重，先到先生效。

### 6.4 `agentId` 字段

每条 AgentEvent 有 `agentId` 标识来源：
- `"coordinator"` — Coordinator 自身
- `"expert-analysis"` / `"expert-writing"` / `"expert-file"` — 对应专家

## 7. SSE 流示例

一个完整对话的 SSE 事件序列：

```
event: userMessage
data: {"type":"userMessage","agentId":"user-a","content":"分析接口安全风险","sessionId":"message-xxx",...}

event: coordinatorPhase
data: {"type":"coordinatorPhase","agentId":"coordinator","status":"analyzing","content":"Coordinator is analyzing the request.",...}

event: coordinatorPhase
data: {"type":"coordinatorPhase","agentId":"coordinator","status":"planning","content":"Coordinator is creating an execution plan.",...}

event: planUpdate
data: {"type":"planUpdate","agentId":"coordinator","tasks":[{"status":"todo","title":"分析接口安全风险"}],...}

event: coordinatorPhase
data: {"type":"coordinatorPhase","agentId":"coordinator","status":"dispatching","content":"Dispatching tasks to experts.",...}

event: newPlanStep
data: {"type":"newPlanStep","agentId":"expert-analysis","content":"Expert expert-analysis accepted the task.",...}

event: liveStatus
data: {"type":"liveStatus","agentId":"expert-analysis","content":"初始化",...}

event: thinkingDelta
data: {"type":"thinkingDelta","agentId":"expert-analysis","text":"分析任务: 分析接口安全风险",...}

event: textDelta
data: {"type":"textDelta","agentId":"expert-analysis","text":"Task completed: 分析接口安全风险",...}

event: chat
data: {"type":"chat","agentId":"expert-analysis","content":"Task completed: 分析接口安全风险","fileType":"common","attachments":[...],"usage":{...},...}

event: end
data: {"type":"end","agentId":"expert-analysis","content":"Task completed: ...",...}

event: coordinatorPhase
data: {"type":"coordinatorPhase","agentId":"coordinator","status":"answering","content":"All expert tasks completed, preparing final response.",...}

event: chat
data: {"type":"chat","agentId":"coordinator","content":"Task completed: ...",...}

event: coordinatorPhase
data: {"type":"coordinatorPhase","agentId":"coordinator","status":"completed","content":"Request completed.",...}
```

## 8. 配置

关键环境变量：

| 变量 | 默认值 | 用途 |
|---|---|---|
| `AGENTCORE_MOCK_ENABLED` | `true` | Mock/真实 AgentCore 切换 |
| `AGENTCORE_BASE_URL` | — | 真实 AgentCore 地址 |
| `AGENTCORE_AUTH_VALUE` | — | AgentCore 认证令牌 |
| `EXECUTION_WORKER_INTERVAL_MS` | `500` | Worker 轮询间隔 |
| `EVENT_DATABASE_POLL_INTERVAL_MS` | `500` | SSE 跨实例同步间隔 |
| `DIGITAL_TEAM_MVP_ENABLED` | `true` | Feature Flag |
| `DIGITAL_TEAM_EMERGENCY_STOP` | `false` | 紧急停止开关 |

## 9. API

### 项目管理

```
POST   /api/v1/projects                                    创建项目
GET    /api/v1/projects/{id}                               查看项目
PATCH  /api/v1/projects/{id}                               更新项目
POST   /api/v1/projects/{id}/members                       添加成员
DELETE /api/v1/projects/{id}/members/{userId}              移除成员
POST   /api/v1/projects/{id}/experts                       添加专家
DELETE /api/v1/projects/{id}/experts/{expertId}            移除专家
```

### 对话与消息

```
POST   /api/v1/projects/{id}/tasks                         创建对话
POST   /api/v1/projects/{id}/tasks/{taskId}/messages       发送消息
GET    /api/v1/projects/{id}/tasks/{taskId}/events         订阅 SSE 事件
DELETE /api/v1/projects/{id}/expert-tasks/{taskId}         取消专家任务
```

### 人工介入

```
POST   /api/v1/projects/{id}/human-requests/{requestId}/responses  回答人工请求
```

### 其他

```
GET    /api/v1/projects/{id}/tasks/{taskId}/workspace      工作区快照
POST   /api/v1/projects/{id}/intent-analysis               意图分析（测试用）
GET    /api/v1/admin/prompts                               管理 Prompt
```

所有 API 需要 `X-Tenant-Id` 和 `X-User-Id` 头。

## 10. 故障恢复

| 场景 | 机制 |
|---|---|
| Worker 实例崩溃 | DB lease 30s 过期，其他实例接管，从 `last_sequence` 续传 |
| AgentCore 返回重复事件 | `event_id` DB unique key 去重 |
| AgentCore 返回乱序事件 | 按 `sequence` 排序 |
| AgentCore Run 丢失 | 合成 `error` 事件，标记任务 FAILED |
| SSE 断线重连 | `Last-Event-ID` 头 + `project_event` 回放 + `AGENT_RUN_MARKER` 触发 AgentCore 重查 |
| 跨实例 SSE 投递 | DB 轮询 `project_event` + `AGENT_RUN_MARKER` → `agentCore.streamEvents()` |
| 人工请求超时 | 定时扫描过期请求，标记任务 FAILED |

## 11. 测试

- **单元测试**: `src/test/.../unit/` — POJO、状态机、权限判断
- **集成测试**: `src/test/.../integration/` — Spring Boot + H2，Mock AgentCore
- **运行**: `mvn verify` (32 tests, 0 failures)

本地依赖：`docker compose up -d` 启动 MySQL 8.0 + Redis 7 + MinIO。
