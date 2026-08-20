# AgentCore 工具开发契约

本文档是 **AgentCore 侧工具开发**的唯一依据：TeamCoordinator 要求 AgentCore 挂载哪几个工具、每个工具做什么、输入输出是什么、事件如何回传。

> 工具定义文件（挂载参考）位于 `src/main/resources/agentcore-tools/*.json`；
> TeamCoordinator 消费端按 **工具名字符串精确匹配**，名字漂移会导致结构化输出回退到 `end.content` 兜底路径。

## 1. 总览

| 工具名 | 挂载到 | 作用 | 输入 | 输出（返回体） |
|---|---|---|---|---|
| `submit_coordinator_decision` | coordinator agent | 提交意图分析决策 | `CoordinatorDecision` JSON（见 §3） | ack 文本（业务无要求） |
| `submit_coordinator_plan` | coordinator agent（规划场景） | 提交执行计划 | `CoordinatorPlan` JSON（见 §4） | ack 文本（业务无要求） |
| `submit_review_verdict` | coordinator agent（审查场景） | 提交语义审查结论 | `{consistent, reason}`（见 §5） | ack 文本（业务无要求） |
| `upload_artifact` | 专家 agent（执行场景） | 上传生成的文件 | multipart `file` 文件part（见 §6） | `ArtifactView` JSON（见 §6.4） |

## 2. 所有工具必须满足的通用要求

### 2.1 行为协议（写进工具描述与系统提示词）

工具描述必须包含（定义文件里已写就，请原样保留语义）：

1. 说明调用时机与参数含义；
2. **"调用工具后直接 `end` 结束 run，不得再输出正文"**；
3. **"Never write the JSON as plain text"** —— 结果只通过工具入参提交，不允许把 JSON 写在正文里。

TeamCoordinator 侧的提示词模板（V20 起）同样点名了工具与协议，见 §7。

### 2.2 TeamCoordinator 消费规则（事件 → 结果）

对 coordinator 的每次 run，消费端按以下优先级取最终结构化结果：

1. **最高优先级**：SSE 流中出现 `toolUsed` 事件且 `tool` 字段等于工具名 → 取 `input` 字段（完整 JSON 对象）作为结果；
2. **回退**：未出现工具调用 → 取 `end` 事件的 `content`（兼容未挂载工具的 agent）；
3. 结果随后仍会经过 **JSON Schema 校验**（与工具入参同一份 schema）→ 失败进入**修复轮**（同一 session 内继续对话、把非法输出回填为 `invalidOutput`）→ 仍失败走 fallback（意图分析 → `ASK_HUMAN`；规划 → 计划失败）。

即：工具调用是强约束，但不是唯一防线。

### 2.3 入参 Schema 一致性

`agentcore-tools/*.json` 里的 `parameters` 与 `src/main/resources/coordinator/*-schema-v1.json` **必须一致**（由 `AgentCoreToolsTest` 守护）。AgentCore 侧开发时，入参校验请使用同一份 schema。

### 2.4 入参即校验对象

AgentCore 平台应在发出 `toolUsed` 事件前用 `parameters` 校验工具入参；不合法的调用不应以 `toolUsed` 事件形式出现在流中。

## 3. `submit_coordinator_decision` —— 意图分析决策

- **挂载**：coordinator agent
- **作用**：对用户消息做出决策并提交：直接回答（ANSWER）、向用户提问（ASK_HUMAN）、创建执行计划（CREATE_PLAN）
- **调用时机**：每次意图分析 run 结束时（`operation=ANALYZE`）；修复轮（`operation=REPAIR`，`invalidOutput` 非空）同样调用本工具提交修正后的决策

### 3.1 输入 Schema（完整）

```json
{
  "type": "object",
  "required": ["decision_type"],
  "properties": {
    "decision_type": {"enum": ["ANSWER", "ASK_HUMAN", "CREATE_PLAN"]},
    "answer": {"type": ["string", "null"]},
    "question": {"type": ["string", "null"]},
    "task_intent": {
      "type": ["object", "null"],
      "properties": {
        "intent": {"type": "string", "minLength": 1},
        "objective": {"type": "string", "minLength": 1},
        "expected_outputs": {"type": "array", "items": {"type": "string"}},
        "constraints": {"type": "array", "items": {"type": "string"}},
        "required_capabilities": {"type": "array", "items": {"type": "string"}},
        "input_refs": {"type": "array", "items": {"type": "string"}},
        "missing_information": {"type": "array", "items": {"type": "string"}},
        "risk_level": {"enum": ["LOW", "MEDIUM", "HIGH"]},
        "execution_mode": {"enum": ["SINGLE_EXPERT", "MULTI_EXPERT"]}
      },
      "required": [
        "intent", "objective", "expected_outputs", "constraints",
        "required_capabilities", "input_refs", "missing_information",
        "risk_level", "execution_mode"
      ]
    }
  },
  "additionalProperties": false
}
```

### 3.2 字段语义（分支互斥要求）

| 决策 | 必填字段 | 语义 |
|---|---|---|
| `ANSWER` | `answer` | 直接回复用户的内容 |
| `ASK_HUMAN` | `question` | 向用户提出的问题（系统会转为 `coordinatorConfirm` 事件） |
| `CREATE_PLAN` | `task_intent`（九字段全必填） | 交给规划阶段的意图描述 |

TeamCoordinator 侧还会做语义形状校验：`ANSWER 缺 answer` / `ASK_HUMAN 缺 question` / `CREATE_PLAN 缺 task_intent` 直接判为非法输出进入修复轮。

### 3.3 输出（工具返回体）

业务无要求——TeamCoordinator **只读 `toolUsed.input`，不读工具返回体**。返回任意 ack 文本即可（如 `"accepted"`）。

### 3.4 消费与失败处理（TeamCoordinator 侧）

`IntentAnalysisService.parse()`：JSON Schema 校验 + bean 校验 + 形状校验；失败 → `prepareRepair`（`invalidOutput` 回填非法输出、run 阶段置 `REPAIR`）→ 同一 session 重跑一次 → 仍失败 → fallback `ASK_HUMAN`（问题："暂时无法可靠理解该请求，请补充目标和期望输出后重试。"）。

## 4. `submit_coordinator_plan` —— 执行计划

- **挂载**：coordinator agent（规划场景）
- **作用**：把 `task_intent` 分解为可执行的专家子任务计划并提交
- **调用时机**：决策为 CREATE_PLAN 后的规划 run；修复轮（最多 2 次）同样调用本工具提交修正后的计划

### 4.1 输入 Schema（完整）

```json
{
  "type": "object",
  "required": ["plan_version", "tasks"],
  "properties": {
    "plan_version": {"type": "integer", "minimum": 1},
    "tasks": {
      "type": "array",
      "minItems": 1,
      "maxItems": 8,
      "items": {
        "type": "object",
        "required": [
          "task_key", "objective", "dependencies", "expected_output",
          "acceptance_criteria", "required_capabilities"
        ],
        "properties": {
          "task_key": {"type": "string", "minLength": 1},
          "objective": {"type": "string", "minLength": 1},
          "dependencies": {"type": "array", "items": {"type": "string"}},
          "expected_output": {"type": "string", "minLength": 1},
          "acceptance_criteria": {"type": "string", "minLength": 1},
          "required_capabilities": {
            "type": "array",
            "minItems": 1,
            "items": {"type": "string"}
          }
        },
        "additionalProperties": false
      }
    }
  },
  "additionalProperties": false
}
```

### 4.2 字段语义与硬性约束

| 字段 | 约束 |
|---|---|
| `plan_version` | 必须等于请求的版本（当前为 1），乱写直接拒 |
| `tasks` | 1~8 个；`task_key` 全局唯一 |
| `dependencies` | 引用其他 `task_key`；引用必须存在、无重复、**深度 ≤ 2**、**无环** |
| `required_capabilities` | 至少 1 项，且必须能被项目启用的专家匹配 |
| `expected_output` / `acceptance_criteria` | 会原样进入专家执行的系统提示词和验收逻辑，请写具体、可判定 |

### 4.3 输出（工具返回体）

同 §3.3——ack 即可，TeamCoordinator 只读 `toolUsed.input`。

### 4.4 消费与失败处理（TeamCoordinator 侧）

`PlanningService.createPlan()` 四道闸：JSON Schema 校验 → 版本校验 → `PlanValidator`（key 唯一/依赖存在/深度/无环/能力匹配）→ **语义评审**（§5 的 `submit_review_verdict`）。任一失败 → 修复轮（同一 session 继续对话，提示词追加失败原因与原计划）→ 最多 2 次 → 仍失败则计划失败（dispatch 置 FAILED）。

## 5. `submit_review_verdict` —— 语义审查结论

- **挂载**：coordinator agent（审查场景）
- **作用**：二判模型对"计划是否服务意图 / 专家结果是否满足验收标准"给出结论
- **调用时机**：规划校验后的语义审查 run、专家结果验收 run

### 5.1 输入 Schema（完整）

```json
{
  "type": "object",
  "required": ["consistent"],
  "properties": {
    "consistent": {"type": "boolean"},
    "reason": {"type": "string"}
  },
  "additionalProperties": false
}
```

| 字段 | 语义 |
|---|---|
| `consistent` | true=通过；false=拒绝（`reason` 必须给出简短原因，会作为修复输入回填） |
| `reason` | 拒绝原因；通过时为空串 |

### 5.2 输出与消费（TeamCoordinator 侧）

工具返回体不读，只读 `toolUsed.input`。**fail-open**：审查 run 没有给出明确结论（工具未挂载/输出非法/无法判定）时**不阻塞**主流程——只有明确的 `consistent: false` 才触发修复/拒绝。这一点请勿在 AgentCore 侧改为 fail-closed。

## 6. `upload_artifact` —— 专家产物上传（HTTP 工具）

- **挂载**：专家 agent（执行场景）
- **作用**：专家生成的文件上传到 TeamCoordinator，返回 `artifactId`；专家须把 `artifactId` 写进结果提交（RUN_SUCCEEDED 的 `artifactIds`），并在 `chat`/`end` 事件的 `attachments` 里声明同一文件
- **性质**：HTTP 工具，与 §3~§5 的"提交工具"不同，它有真实的服务端执行

### 6.1 端点契约

```
POST {baseUrl}/api/v1/agent-tools/projects/{projectId}/tasks/{taskId}/artifacts
Content-Type: multipart/form-data（part 名 "file"，保留原始文件名与 media type）
```

- `{baseUrl}`：TeamCoordinator 部署地址，**挂载时在 AgentCore 侧配置**（仓库只定义相对路径）
- `{projectId}` / `{taskId}`：TeamCoordinator 的项目 businessId / 协调任务 businessId，**由 run 的 structured input 注入**（字段 `projectId`、`taskId`，`startTask` 已随 run request 下发）

### 6.2 请求头

| Header | 来源 |
|---|---|
| `X-AgentCore-Tool-Token` | 配置的共享密钥（TeamCoordinator 侧 `AGENTCORE_ARTIFACT_TOOL_TOKEN`） |
| `X-Session-Id` | structured input 的 `businessSessionId` |
| `X-Agent-Run-Id` | 本次 agent run 的 id |
| `X-Agent-Id` | 专家 agent id |

### 6.3 服务端校验

1. token 恒定时间比较，不匹配 → 401；
2. 归属查证：`(projectId, taskId, businessSessionId, agentRunId, agentId)` 必须对应真实存在的任务与 run，否则 403；
3. 文件名非空、内容非空、大小上限内。

### 6.4 输出（返回体 `ArtifactView`）

```json
{
  "artifactId": "artifact-<uuid>",
  "version": 1,
  "fileName": "risk-report.pdf",
  "mediaType": "application/pdf",
  "size": 12345,
  "sha256": "...",
  "status": "UPLOADING",
  "uploadUrl": null,
  "downloadUrl": null
}
```

`artifactId` 是业务 ID，专家须把它写进 RUN_SUCCEEDED 的 `artifactIds`，并在 `chat`/`end` 事件 `attachments` 里以 `path` 引用同一存储键（`{"fileName":..., "contentType":..., "pathType":"output", "path":"<上传时返回/记录的文件键>"}`）。

### 6.5 消费（TeamCoordinator 侧）

任务 SUCCEEDED 时 `registerExpertArtifact`：**二次校验文件真实存在**（"只声明未上传"会失败）→ 落库 `project_artifact`（状态置 AVAILABLE、sha256、版本递增）→ 记录输入血缘。下游任务通过依赖关系自动获得这些产物作为附件。

## 7. 提示词侧要求（TeamCoordinator 已就绪，V18 起生效）

| 提示词 | 当前版本 | 点名工具（提示词原句要点） |
|---|---|---|
| `coordinator.execution` | v3 | `submit_coordinator_decision`："You MUST submit your decision by calling the submit_coordinator_decision tool with the decision JSON conforming to output_schema as the tool arguments. Never write the decision JSON as plain text. After a successful tool call, end the run without further output." |
| `coordinator.planning` | v3 | `submit_coordinator_plan`（同上协议） |
| `coordinator.plan_check` | v2 | `submit_review_verdict`（同上协议） |
| `expert.result_check` | v2 | `submit_review_verdict`（同上协议） |
| `expert.execution` | v1 | `upload_artifact`："Generated files must be uploaded with upload_artifact; include returned artifactIds in RUN_SUCCEEDED." |

## 8. SSE 事件契约（AgentCore → TeamCoordinator）

每次 run 的事件流中，以下事件类型被 TeamCoordinator 消费（其余类型按进度透传）：

| 事件 type | 关键字段 | TeamCoordinator 消费行为 |
|---|---|---|
| `toolUsed` | `tool`（工具名）、`input`（结构化 JSON） | §3~§5 的结构化结果来源（最高优先级） |
| `chat` | `content`、`attachments`、`usage` | 专家结果正文 + 产物声明 |
| `end` | `content` | run 终态信号；`content` 为结构化结果回退来源；专家侧同时作结果兜底 |
| `error` | `status`（TIMED_OUT/FAILED）、`content` | 终态失败 |
| `confirm` | `content`、`questionId`、`questions` | 专家求助 → 任务置 WAITING_HUMAN |
| `liveStatus`/`textDelta`/`thinking*`/`toolResult`/`planUpdate`/`newPlanStep` 等 | — | 进度，live 透传给客户端，不影响状态机 |

规则：工具调用完成后 run **必须发出 `end` 事件**；事件需带稳定可递增的序号与唯一 eventId（断线重放依赖）。

## 9. 开发完成对照清单

- [ ] 四个工具已挂载到对应 agent，`name` 与定义文件逐字一致
- [ ] 入参按 §3.1/§4.1/§5.1 schema 校验，`additionalProperties: false` 生效
- [ ] 工具描述含"调用后 end、不得正文输出 JSON"协议
- [ ] `toolUsed` 事件携带完整 `input`，随后 run 以 `end` 结束
- [ ] `upload_artifact` 指向 TeamCoordinator 端点，四个 header 由 run 上下文注入
- [ ] 专家结果中 `artifactId` 写入 RUN_SUCCEEDED，且 `chat`/`end` 事件带 `attachments` 声明
- [ ] 未挂载工具时 `end.content` 兜底路径可用（输出合法 JSON 文本）
