# tc — TeamCoordinator 配套 CLI

在 AgentCore 运行机器上执行的命令行工具：agent 通过内置 shell 能力调用本 CLI，
把**决定后续动作的格式化数据**直写回 TeamCoordinator 的表（决策 / 计划 / 审查结论），
或上传产出物文件——不再依赖 TeamCoordinator 解析流式文本。

- 零依赖：Node 18+（AgentCore 基于 Claude Agent SDK，Node 必然存在），Linux/macOS 通用
- 本地校验：内置与 `coordinator/*-schema-v1.json` 一致的校验逻辑（由
  `CliSchemaConsistencyTest` 守护 schema 副本一致性），提交前即时拦截格式错误

## 配置（AgentCore 在 `query()` 时注入环境变量）

| 环境变量 | 说明 |
|---|---|
| `TC_BASE_URL` | TeamCoordinator 部署地址（挂载时在 AgentCore 侧配置） |
| `TC_TOKEN` | 共享密钥，对应 TeamCoordinator 的 `AGENTCORE_ARTIFACT_TOOL_TOKEN` |
| `TC_SESSION` | 当前 coordinator run 的 AgentCore session id（提交归属键） |
| `TC_PROJECT` / `TC_TASK` | 项目 businessId / 协调任务 businessId（仅 upload-artifact 需要） |
| `TC_RUN` / `TC_AGENT` | 当前 run id / agent id（仅 upload-artifact 需要） |

## 命令

```bash
# 提交决策（ANSWER / ASK_HUMAN / CREATE_PLAN）
cat decision.json | tc submit-decision
tc submit-decision --file decision.json

# 提交执行计划（写入 coordinator_plan / coordinator_task，推动执行引擎）
tc submit-plan --file plan.json

# 提交语义审查结论 {"consistent": bool, "reason": "..."}
tc submit-verdict --file verdict.json

# 上传产出物（multipart），返回 ArtifactView JSON（含 artifactId）
tc upload-artifact result.pdf

# 仅本地校验（不发请求）
tc validate decision --file decision.json

# 连通性检查
tc health
```

退出码：`0` 成功；`1` 校验失败或传输失败（错误信息输出到 stderr）。

## 提示词注入

AgentCore 调用 coordinator agent 时使用 CLI 版提示词（`coordinator.execution` v4 /
`coordinator.planning` v4 / `coordinator.plan_check` v2+，见 V22 迁移）：agent 在 run 内
生成 JSON 后调用 `tc submit-decision` / `tc submit-plan` / `tc submit-verdict` 提交，
然后结束 run。TeamCoordinator 消费优先级：
**CLI 提交记录 > toolUsed(input) > end.content**。

## 与 schema 文件的一致性

`schemas/` 下的 JSON 是 `src/main/resources/coordinator/*-schema-v1.json` 的副本，
由 `CliSchemaConsistencyTest`（Java 单元测试）守护：修改任何一侧都必须同步另一侧。
