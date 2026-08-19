# AgentCore Submission Tools

结构化输出契约的工具定义。TeamCoordinator 通过要求 AgentCore agent **调用工具**来提交结构化结果，从而把输出格式约束前移到生成期（平台会对工具入参做 schema 校验）。

## 挂载方式

将这些工具挂载给对应的 agent（挂载由 AgentCore 平台侧完成）：

| 工具名 | 挂载到 | 用途 | 入参 Schema |
|---|---|---|---|
| `submit_coordinator_decision` | coordinator agent | 提交决策（ANSWER / ASK_HUMAN / CREATE_PLAN） | `coordinator/task-intent-schema-v1.json` |
| `submit_coordinator_plan` | coordinator agent（规划场景） | 提交执行计划 | `coordinator/plan-schema-v1.json` |
| `submit_review_verdict` | coordinator agent（审查场景） | 提交语义审查结论 `{"consistent", "reason"}` | 见工具定义文件 |

> 注意：`submit_coordinator_decision` / `submit_coordinator_plan` 的 `parameters` 与
> `src/main/resources/coordinator/*-schema-v1.json` 必须保持一致（由
> `AgentCoreToolsTest` 守护），修改任何一侧都要同步另一侧。

## 事件契约（AgentCore → TeamCoordinator）

1. agent 调用工具 → SSE 发出 `toolUsed` 事件，`tool` 为工具名，`input` 为结构化 JSON；
2. 工具调用完成后 run 必须发出 `end` 事件结束（prompt 中同样要求）。

TeamCoordinator 消费规则：

- 流中出现上述工具名的 `toolUsed` 事件时，以 **`input` 为最终结构化结果**（最高优先级）；
- 未出现工具调用时回退到 `end` 事件的 `content`（兼容尚未挂载工具的 agent）；
- 结果随后仍会经过 JSON Schema 校验、语义审查与修复兜底——工具调用是强约束，但不是唯一防线。
