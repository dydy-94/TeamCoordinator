# tc — TeamCoordinator 配套 CLI

在 AgentCore 运行机器上执行的命令行工具：agent 通过内置 shell 能力调用本 CLI，
把**决定后续动作的格式化数据**直写回 TeamCoordinator 的表（决策 / 计划 / 审查结论），
或上传产出物文件——不再依赖 TeamCoordinator 解析流式文本。

- 零依赖：Node 18+（AgentCore 基于 Claude Agent SDK，Node 必然存在），Linux/macOS 通用
- 本地校验：内置与 `coordinator/*-schema-v1.json` 一致的校验逻辑（由
  `CliSchemaConsistencyTest` 守护 schema 副本一致性），提交前即时拦截格式错误

## 配置（安装时设置环境变量）

| 环境变量 | 说明 |
|---|---|
| `TC_BASE_URL` | TeamCoordinator 部署地址 |
| `TC_TOKEN` | 共享密钥，对应 TeamCoordinator 的 `AGENTCORE_ARTIFACT_TOOL_TOKEN` |

**Per-run 上下文（task id 等）一律通过命令参数传入，不依赖环境注入**——task id 是
Coordinator、AgentCore 与 CLI 三方唯一可靠共享的标识：

- coordinator 的 decision/plan/verdict 提交 → 传**会话 task id**（提示词
  `coordinator_context.conversation_task_id` 里给了）
- 专家的任务拉取/结果写回/产出物上传 → 传**协调任务 task id**（dispatch 时下发）

## 命令

```bash
# 提交决策（ANSWER / ASK_HUMAN / CREATE_PLAN）
tc submit-decision --task <会话taskId> --file decision.json

# 提交执行计划（写入 coordinator_plan / coordinator_task，推动执行引擎）
tc submit-plan --task <会话taskId> --file plan.json

# 提交语义审查结论 {"consistent": bool, "reason": "..."}
tc submit-verdict --task <会话taskId> --file verdict.json

# 拉取任务详情（渲染后的执行提示词 + 验收标准 + 上游产物）
tc get-task --task <协调taskId>

# 写回执行结果（result_json + SUCCEEDED）
tc submit-result --task <协调taskId> --file result.txt
# 或 tc submit-result --task <协调taskId> --text "结果文本"

# 上传产出物（multipart），返回 ArtifactView JSON（含 artifactId）
tc upload-artifact --task <协调taskId> result.pdf

# 仅本地校验（不发请求）
tc validate decision --file decision.json

# 连通性检查
tc health
```

退出码：`0` 成功；`1` 校验失败或传输失败（错误信息输出到 stderr）。

## 提示词注入

AgentCore 调用 agent 时使用 task-id 键控的 CLI 版提示词（V24 迁移）：
`coordinator.execution` v5 / `coordinator.planning` v5 / `coordinator.plan_check` v4 /
`expert.result_check` v4 / `expert.execution` v3——coordinator 在 run 内生成 JSON 后调用
`tc submit-decision --task <会话taskId>` 等提交；专家被派发时只拿到 task id，通过
`tc get-task` 拉取完整契约、`tc submit-result` 写回结果、`tc upload-artifact` 上传产物。
TeamCoordinator 消费优先级：**CLI 提交记录 > toolUsed(input) > end.content**（提交按
(task, kind) 幂等，消费后删除防跨消息串用）。

## 与 schema 文件的一致性

`schemas/` 下的 JSON 是 `src/main/resources/coordinator/*-schema-v1.json` 的副本，
由 `CliSchemaConsistencyTest`（Java 单元测试）守护：修改任何一侧都必须同步另一侧。
