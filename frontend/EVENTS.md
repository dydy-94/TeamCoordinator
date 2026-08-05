# TeamCoordinator SSE 事件类型

> 更新: 2026-08-05

## 总览

Coordinator **透传** AgentCore 的所有 SSE 事件（不解析、不转换 type），同时自己产生少量事件用于生命周期管理。两端通过 `agentId` 字段区分来源：

| agentId | 来源 |
|---------|------|
| `"coordinator"` | Coordinator 自身生成 |
| `"expert-analysis"` / `"expert-writing"` / `"expert-file"` / 其他 | AgentCore 透传 |
| 其他字符串 | 用户消息（userMessage） |

---

## AgentCore 透传事件（30+ 种）

这些事件从 AgentCore 的 SSE 流中原样解析，type **完全不修改**。后续 AgentCore 新增的事件类型会自动透传。

### 对话输出

| type | 说明 | 关键字段 |
|------|------|---------|
| `chat` | Agent 回复消息 | `content`, `attachments`, `usage`, `fileType` |
| `textDelta` | 流式文本片段 | `text` |
| `streamStart` | 流式输出开始 | `blockType` |
| `streamEnd` | 流式输出结束 | `totalTime` |

### 思考过程

| type | 说明 | 关键字段 |
|------|------|---------|
| `thinkingStart` | 开始思考 | `blockType` |
| `thinkingDelta` | 思考流式片段 | `text` |
| `thinking` | 全量思考内容 | `text`, `usage` |
| `thinkingEnd` | 思考结束 | `totalTime` |

### 计划与步骤（Agent 内部）

| type | 说明 | 关键字段 |
|------|------|---------|
| `planUpdate` | Agent 内部计划状态 | `tasks[]` |
| `newPlanStep` | Agent 步骤开始 | `content` |

### 状态与生命周期

| type | 说明 | 关键字段 |
|------|------|---------|
| `liveStatus` | 实时状态更新 | `content`（初始化、思考中、请求模型...） |
| `taskInQueue` | 排队等待 | `content` |
| `end` | 执行结束 | `content`, `attachments`, `usage` |
| `error` | 执行异常 | `content` |
| `confirm` | Agent 反问用户 | `questionId`, `questions[]`, `content` |

### 工具调用

| type | 说明 | 关键字段 |
|------|------|---------|
| `toolUsed` | 工具开始调用 | `tool`, `input`, `toolUseId`, `parentToolUseId` |
| `toolResult` | 工具调用结果 | `toolName`, `output`, `toolUseId`, `parentToolUseId` |

### 子 Agent

| type | 说明 | 关键字段 |
|------|------|---------|
| `subagentThinking` | 子 Agent 思考 | `text`, `parentToolUseId`, `usage` |
| `subagentChat` | 子 Agent 回复 | `content`, `parentToolUseId`, `usage` |
| `subagentToolUsed` | 子 Agent 工具调用 | `tool`, `input`, `toolUseId`, `parentToolUseId` |
| `subagentToolResult` | 子 Agent 工具结果 | `toolName`, `output`, `toolUseId`, `parentToolUseId` |

### 文件操作

| type | 说明 | 关键字段 |
|------|------|---------|
| `file` | 文件信息 | `fileName`, `contentType`, `path` |
| `directory` | 目录信息 | `name`, `path` |
| `streamingFile` | 流式写文件 | `fileName`, `contentType`, `path`, `toolUseId`, `parentToolUseId` |

### 其他

| type | 说明 | 关键字段 |
|------|------|---------|
| `sidebarDisplay` | 工作区展示 | `mode`（excalidraw/vnc...） |
| `weblink` | 打开链接 | `content`, `path` |
| `reconnect` | 重连通知 | `content`, `path` |
| `clearBoundary` | 清理上下文界限 | — |
| `compactBoundary` | 压缩上下文界限 | — |

---

## Coordinator 生成事件（7 种）

这些事件在 Coordinator 代码中创建。通过 `agentId: "coordinator"` 区分来源。

### 独立类型（2 种）

仅 Coordinator 产生，AgentCore 不会发。

| type | 说明 | 触发时机 |
|------|------|---------|
| `userMessage` | 用户发送消息 | `CoordinatorMessageService.accept()` |
| `coordinatorPhase` | 协调器阶段转换 | `SingleExpertWorker.emitPhase()`，`status` 取值：`analyzing` / `planning` / `dispatching` / `answering` / `waiting_human` / `completed` / `failed` |
| `coordinatorChat` | ANSWER 文本 / 专家完成后的汇总回复 | `SingleExpertWorker.process()` |
| `coordinatorConfirm` | Coordinator ASK_HUMAN 反问 | `SingleExpertWorker.process()` |
| `coordinatorError` | 协调过程异常（专家选择失败、执行失败等） | `SingleExpertWorker` 多处 |
| `coordinatorPlanUpdate` | 协调器创建执行计划 | `SingleExpertWorker.process()` |
| `coordinatorNewPlanStep` | 任务分配给专家开始执行 | `SingleExpertWorker.startTask()` |
| `coordinatorRunCancelled` | 用户取消任务 | `SingleExpertWorker.cancel()` |

所有 Coordinator 事件都以 `coordinator` 前缀开头，与 AgentCore 事件不重名。

---

## 持久化与回放

| 事件来源 | 落 `project_event` | 回放方式 |
|---------|-------------------|---------|
| Coordinator 生成 | ✅ 是 | DB 直接读取 `payload` 字段 |
| AgentCore 透传 | ❌ 否（只存 `AGENT_RUN_MARKER`） | 通过 `marker.sessionId` 调 `agentCore.streamEvents()` 现场拉取 |

---

## 前端消费

前端通过 SSE 连接获取所有事件，根据 `agentId` 区分来源，根据 `type` 决定渲染方式：

- `userMessage` → 用户气泡（右）
- 其他所有 → 聚合到同一个回复气泡，逐步追加内容
- `confirm` / `coordinatorPhase[waiting_human]` → 弹出 HITL 回答面板
