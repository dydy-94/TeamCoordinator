# TeamCoordinator 业务逻辑说明

> 本文档描述当前 main 分支的完整业务逻辑（CLI-only 产出通道 + taskId 键控 + DDD 四层架构）。
> 配套文档：`docs/refactoring-plan.md`（架构重构方案）、`src/main/cli/README.md`（tc CLI 用法）、
> `db/init/01-schema.sql` + `db/init/02-seed.sql`（**完整数据库定义与种子**，脱离 Flyway 的初始化源，compose 自动挂载执行）、
> `docs/agentcore-tool-contract.md`（平台工具契约，已由 CLI 通道取代，留作历史参考）。

## 1. 系统定位

TeamCoordinator 是一个 AI agent 编排服务：

- 接收用户消息，调用配置的 **coordinator agent**（运行在 AgentCore 上）理解意图
- 把意图转化为**执行计划**（子任务 + 依赖 + 能力要求）
- 把子任务派发给**专家 agent** 执行，管理依赖顺序与结果汇总
- 全程通过 SSE 把过程事件推送给前端

**核心架构原则（当前版本）**：产出通道与展示通道分离——

| 通道 | 内容 | 机制 |
|---|---|---|
| 产出通道（唯一） | 决策/计划/专家结果/人类问题/产物 | 全部经 `tc` CLI 直写 TeamCoordinator 表，服务端校验 |
| 展示通道 | 思考过程、工具调用、状态变化 | AgentCore 事件流逐事件透传前端 SSE，**不驱动状态机**（`error` 事件除外） |

## 2. 核心概念与数据模型

| 概念 | 表 | 说明 |
|---|---|---|
| 会话任务 | `project_conversation` | 用户创建的对话容器（用户先建 task 再发消息），跨多轮消息长存 |
| 消息 | `project_message` | 用户在会话任务下发送的每条消息 |
| 执行票 | `coordinator_dispatch` | 每条消息对应一张，worker 领取执行；含租约、attempt_count、状态机 |
| 意图分析记录 | `coordinator_analysis` | 每次意图分析的输入快照与决策 JSON |
| 执行计划 | `coordinator_plan` | 一个消息的分解方案（plan_version、任务列表）；replan 会产生多版本 |
| 子任务 | `coordinator_task` | 计划里的可执行单元：objective/expected_output/acceptance_criteria/dependencies/required_capabilities/expert_id/result_json/状态机 |
| CLI 提交 | `coordinator_cli_submission` | agent 经 tc 命令提交的原始结构化载荷，按 (task_id, kind) 幂等，**消费后删除** |
| 人类请求 | `human_request` | 协调者提问或专家求助，含问题、状态（PENDING/RESOLVED/EXPIRED）、归属 task |
| 产物 | `project_artifact` | 专家上传的文件（版本、sha256、状态 AVAILABLE），含依赖血缘 |
| 事件 | `project_event` | 面向前端的生命周期事件（可持久化重放）；agent 原始事件不落库，经 MARKER 重放 |
| SSE 生命周期 | 内存（hub） | 每 60s 心跳注释帧保活；按"最近有新事件"刷新活动时间，超 30 分钟无活动发 `inactive` 命名事件后强制断开（配置：`EVENT_HEARTBEAT_INTERVAL_MS` / `EVENT_INACTIVITY_TIMEOUT_MIN`） |

**taskId 是三方唯一共享标识**：AgentCore 运行期无法感知自身 session id，因此所有
CLI ↔ TeamCoordinator 交互都以 taskId 键控：

- coordinator 的决策/计划提交 → **会话 taskId**（= `project_conversation.business_id`，注入在提示词上下文 `conversation_task_id`）
- 专家的任务拉取/结果写回/提问/上传 → **协调任务 taskId**（= `coordinator_task.business_id`，派发时下发）

## 3. 完整业务流程

```
用户发消息 ─▶ ①同步入口 ─▶ ②worker 领取 ─▶ ③意图分析(CLI 决策)
   ├─ ANSWER      → 直接回答，完成
   ├─ ASK_HUMAN   → 提问挂起，等用户下条消息
   └─ CREATE_PLAN → ④CLI 计划直写 → ⑤依赖驱动的专家执行循环
                      └─ 专家: tc get-task 拉契约 → ask-human / upload-artifact /
                         submit-result 交互 → ⑥全部完成 → 汇总最终回答
全程: ⑦事件流只做展示推 SSE；⑧容错机制全程生效
```

### ① 消息入口（同步）

`POST /api/v1/projects/{projectId}/tasks/{taskId}/messages`
→ `CoordinatorMessageService.accept`（一个事务内）：

1. 鉴权（任务发起者角色）+ 会话归属校验
2. `client_message_id` 幂等去重
3. 落 `project_message`；广播 `userMessage`、`coordinatorPhase(analyzing)` 事件
4. 插 `coordinator_dispatch` 票（状态 PENDING）
5. 事务提交后推 SSE；返回 202

### ② Worker 领取（异步引擎）

`SingleExpertWorker.runOnce()` 每 500ms（`EXECUTION_WORKER_INTERVAL_MS`）：

- **租约领取**：`claimNext` 以数据库租约原子抢占（30s），同一会话只允许最旧的一条 PENDING/RUNNING 票被领取 → **会话内消息严格串行**
- **心跳续租**：领取后守护线程每 10s 续租，处理完成即取消——多实例下长任务不会被重复领取
- 领取后 `process(work)`：有子任务 → 推进计划；新消息 → 意图分析

### ③ 意图分析（coordinator agent，产出走 CLI）

1. 构建上下文：项目名/描述、启用的专家清单、附件、pending 人类请求状态、`conversation_task_id`
2. 渲染 `coordinator.execution` v6 提示词（注入决策 JSON Schema 全文）提交到 AgentCore 的 coordinator agent，**复用 task 级 coordinator session** 保持跨消息上下文
3. agent 在 run 内生成决策 JSON → `tc submit-decision --task <会话taskId> --file decision.json` 直写提交表
4. worker 拉事件流直到 `end`：读提交表取决策；**run 结束却没有提交 → 该次执行明确失败**（不再解析流文本、不再修复、不再兜底）

决策三分支：

| 决策 | 行为 |
|---|---|
| ANSWER | 发 `coordinatorChat` 直接回答 + `completed` 阶段，dispatch COMPLETED |
| ASK_HUMAN | 建 human_request（问题）+ 发 `coordinatorConfirm` 事件，dispatch WAITING_HUMAN；等用户下条消息 |
| CREATE_PLAN | 进入计划环节（④） |

### ④ 计划落库（coordinator agent 提交，服务端直写）

同一 run 内 agent 继续生成计划 JSON → `tc submit-plan --task <会话taskId> --file plan.json`：

- 服务端校验：JSON Schema（plan-schema-v1，≤8 任务/深度≤2 语义由 CLI 本地校验先行）+ `plan_version=1` + 分支形状
- **直写** `coordinator_plan` + `coordinator_task`（幂等：同消息已有计划则复用）；决策缺失时仅存储，worker 后补
- worker 的 CREATE_PLAN 分支：读提交表取计划（消费后删除），缺失 → 明确失败
- 发 `coordinatorPlanUpdate` 事件（任务列表初始 todo）

### ⑤ 专家执行循环（taskId-only 派发 + 拉取式契约）

`advancePlan` 每轮：

1. **消费事件**：对 RUNNING 任务拉 AgentCore 事件流——只做展示透传 + 游标推进（进度事件带 `status='RUNNING'` 守卫，不会把 CLI 置为 WAITING_HUMAN 的任务打回 RUNNING）；`error` 事件仍为终态信号（FAILED/TIMED_OUT）
2. **终态判定**：全部 SUCCEEDED → 汇总最终回答；有 FAILED/CANCELLED → 计划失败
3. **STARTING 恢复**：崩溃遗留的 STARTING+无 session 任务超 60s 重置 PENDING
4. **派发**：依赖已 SUCCEEDED 的 PENDING 任务 → `ExpertSelector`（能力匹配 + 项目允许 + 并发上限 + 最小负载）→ 原子抢占（PENDING→STARTING）→ `startTask`

**startTask 只发 taskId + 最小标识**（`projectId/taskId/tenantId/businessSessionId` 与专家跨消息 session 复用），专家侧：

```
tc get-task --task <taskId>          拉取契约（服务端渲染 EXPERT_EXECUTION 提示词
                                     + 验收标准 + 上游产物下载链接）
   ... 执行 ...
tc ask-human --task <taskId>         需要用户输入 → 任务 RUNNING→WAITING_HUMAN + 建问题
tc upload-artifact --task <taskId> f 上传产物（版本/血缘，供下游任务使用）
tc submit-result --task <taskId>     写回结果 → result_json + SUCCEEDED（终态守卫，
                                     重复/终态后提交返回 409；同时持久化专家 session
                                     供后续消息复用）
```

### ⑥ 完成与最终回答

计划全部任务 SUCCEEDED → 取最后一个任务的结果文本作为 `coordinatorChat` 最终回答 → plan/dispatch COMPLETED → SSE 推前端。

### ⑦ 人在环（两条路径，均不依赖事件流）

| 提问方 | 提问 | 回答 | 继续 |
|---|---|---|---|
| 协调者 | ASK_HUMAN 决策（CLI 提交） | 用户再发一条消息，意图分析注入 pending 状态，coordinator 判断是"回答"还是"新请求" | 新 dispatch 正常执行 |
| 专家 | `tc ask-human` 直写 WAITING_HUMAN | 用户在前端回答 → 服务端 `resumeRun(session, answer)`（session 从 `coordinator_task.session_id` 查，**agent 全程无感知**） | 专家同一 session 继续，最终 `tc submit-result` |

等待期间：会话内串行保证——同一会话的后续消息不会并行执行；协调者提问时 dispatch=WAITING_HUMAN（终态），专家求助时子任务=WAITING_HUMAN（dispatch 仍 RUNNING 等待）。

### ⑧ 容错机制（全程生效）

| 机制 | 行为 |
|---|---|
| 租约 + 心跳续租 | 处理期间 lease 每 10s 续期（带 owner 校验），崩溃后 30s 内被接管 |
| 瞬时故障容忍 | 拉流异常/run 不可见按 `consecutive_failures` 计数（默认 60 次），达阈值才失败 |
| STARTING 恢复 | 崩溃遗留任务 60s 后重置 PENDING 重派发 |
| 终态守卫 | 所有状态改写带终态守卫（`advanceTask` 序列守卫 / `markTaskSucceeded`/`cancelTask` 终态守卫），防并发脏写 |
| 幂等 | 消息按 client_message_id；CLI 提交按 (task, kind)；计划按 (tenant,project,message) |
| 失败口径 | run 结束无决策/计划提交 → 明确失败并记录原因，绝不静默 |

## 4. tc CLI 命令总览

```bash
tc submit-decision --task <会话taskId> --file decision.json   # 决策（ANSWER/ASK_HUMAN/CREATE_PLAN）
tc submit-plan     --task <会话taskId> --file plan.json       # 计划（直写 plan+tasks）
tc submit-verdict  --task <会话taskId> --file verdict.json    # 审查结论
tc get-task        --task <协调taskId>                         # 拉取专家任务契约（含附件列表）
tc get-artifact    --task <协调taskId> [--name 文件] [--output 路径]  # 下载产物
tc submit-result   --task <协调taskId> --text "..."            # 写回专家结果
tc ask-human       --task <协调taskId> --question "..."        # 专家求助
tc upload-artifact --task <协调taskId> <file>                  # 上传产物
tc validate decision|plan|verdict --file f.json               # 仅本地校验
tc health                                                      # 连通性检查
```

- 安装时配置环境变量：`TC_BASE_URL`（TeamCoordinator 地址）、`TC_TOKEN`（共享密钥）
- 所有 per-run 上下文走命令行参数，本地内置与运行时一致的 schema 校验（`CliSchemaConsistencyTest` 守护）

## 5. 状态机

**dispatch**：PENDING → RUNNING → COMPLETED / FAILED / WAITING_HUMAN / CANCELLED

**coordinator_task**：

```
PENDING ──assignExpert──▶ STARTING ──saveSession──▶ RUNNING
RUNNING ──tc submit-result──▶ SUCCEEDED
RUNNING ──tc ask-human──▶ WAITING_HUMAN ──resume──▶ RUNNING
RUNNING ──error 事件──▶ FAILED / TIMED_OUT
任意非终态 ──cancel──▶ CANCELLED
```

## 6. 提示词版本（当前生效）

| 提示词 | 版本 | 契约 |
|---|---|---|
| `coordinator.execution` | v6 | 决策+计划都经 tc 提交（--task 键控），失败按错误信息修正重试 |
| `expert.execution` | v4 | get-task 拉契约 / ask-human 求助 / upload-artifact 传产物 / submit-result 写结果 |

## 7. 关键设计决策与取舍

1. **产出走 CLI、流走展示**：文本解析是历史不稳定性的根源；CLI 直写把校验前移（本地+服务端），agent 可即时重试，服务端有最终裁决权
2. **taskId 键控**：session id 对 agent 不可感知，task id 是三方唯一可靠共享标识；提交消费后删除防跨消息串用
3. **专家派发只发 taskId**：推送大 prompt 变为拉取契约——prompt 渲染与版本管理留在协调器侧，AgentCore 侧零配置
4. **已放弃的自动质量闸**：修复轮、语义二次评审、失败修正任务随流解析一起退役。当前防线 = CLI 本地校验 + 服务端校验 + 终态守卫；如需结果质检，建议以计划中的 review 子任务形式显式编排，而非隐藏自动链
5. **人在环不碰 session 原则**：只有服务端之间的调用（resumeRun）使用 session；agent 与 CLI 之间永远只有 taskId
