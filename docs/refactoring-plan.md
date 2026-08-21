# TeamCoordinator 重构计划：MyBatis DAO+XML + DDD 四层架构

## Context

现状：数据访问层是"1 个泛型 mapper（`DatabaseMapper` @SelectProvider）+ `BoundSqlStatement` 运行时把 `?` 改写为 `#{parameters[i]}`"的方案——约 130 条 SQL 内联字符串散落 11 个 Repository/Controller，无 XML、无 resultMap，`WorkspaceController` 直连 SQL 返回裸 Map；122 个类全部堆在旧包 `org.cmb.teamcoordinator.*`。

目标：① 数据访问改为标准 MyBatis（mapper 接口 + XML）；② DDD 四层（`org.cmb.application` 领域模型+操作 / `org.cmb.common` 工具枚举转换 / `org.cmb.infrastructure` 外部调用+数据库 / `org.cmb.presentation` api 接口），目录用户已建好（当前全空）。

已确认决策：**分阶段提交**（每阶段 `mvn verify` 全绿、独立 commit）；迁移完成后**彻底删除旧包**。执行开始前，把本计划落为 `docs/refactoring-plan.md` 随阶段 1 提交。

## 关键设计决策（定论）

1. **目标布局**（全部平铺，不按模块分子包——领域对象跨模块互引用密集，平铺=机械替换包前缀）：
   - `org.cmb.common.{config,exception,enums,util}`：DigitalTeamProperties；ApiException/PlanValidationException；9 个枚举
   - `org.cmb.application.{domain,dto,service}`：26 个领域 POJO + 7 个端口接口 + 协议类型（AgentEvent/AgentRunRequest/AgentCoreTools/ExpertDescriptor 等）放 domain；8 View+7 Request+ProjectRequests 等放 dto；8 个 @Service + CoordinatorAgentClient + 5 个校验器放 service
   - `org.cmb.infrastructure.{persistent,remoteaccess,worker}`：10 个 Repository 门面 + `persistent/mapper`（11 个 Mapper 接口）+ `persistent/typehandler`（2 个新 TypeHandler）；Http/Mock 客户端与 FileStore 实现；SingleExpertWorker + ProjectEventStreamHub
   - `org.cmb.presentation.{controller,filter,exception}`：16 个 @RestController；2 个 Filter；ApiExceptionHandler
   - 主类 → `org.cmb.TeamCoordinatorApplication`，`scanBasePackages="org.cmb"` + `@MapperScan("org.cmb.infrastructure.persistent.mapper")`
   - XML → `src/main/resources/mapper/*.xml`，文件名=接口名，namespace=接口全限定名
2. **Repository 门面全部保留**（10 个）：3 个集成测试直接 autowire 并调用其方法（findTask/findRecentMessageTexts 等），门面承载 @Transactional 多语句边界与 JSON 序列化；SQL 与行映射全部下沉 mapper/XML。
3. **MyBatis 配置**：`mybatis.mapper-locations: classpath:mapper/*.xml` + `map-underscore-to-camel-case: true`（main 与 test 两份 yml 都加）。别名 `AS taskId` 无下划线不受影响；`AS database_id` → databaseId 正常。
4. **映射约定**：默认 resultType=POJO 自动映射（Instant/枚举 MyBatis 3.5 内置支持）；列名不一致优先写别名；`SELECT *` 全部改显式列清单（9 处，避免代理键 `id` 误映射，需要时写 `t.id AS database_id`）；JSON TEXT→`List<String>` 用 `JsonListTypeHandler`、→`JsonNode` 用 `JsonNodeTypeHandler`（新类放 persistent/typehandler，仅 resultMap 局部挂载）；INSERT 侧参数仍由门面 objectMapper 序列化传 String。
5. **"取首行"语义**：现有 queryForList→first 的查询，mapper 方法保持 `List<T>` 返回、门面取首行（避免 TooManyResultsException 语义漂移）。
6. **动态 SQL**：ArtifactRepository 手拼 IN 占位符 → `<foreach>`；PromptRepository.list 双分支 → `<if>`；其余固定 SQL 原样照抄（含 ON DUPLICATE KEY UPDATE/VALUES()、LIMIT、CURRENT_TIMESTAMP、IN 子查询——H2 MODE=MySQL 已验证兼容，不做任何"现代化"改写）。
7. **WorkspaceController** 直连 SQL → 新建 `WorkspaceMapper` + `WorkspaceService`（application/service），返回 Map 的 key 与现有列名完全一致，前端零影响。
8. **测试契约**：Repository 公开方法签名/bean 名零改动；9 个 JdbcTemplate 断言的表名/列名/状态字符串零改动；Java 迁移文件（V10/V13/V6_1/V7_1）零改动。
9. **禁区**：纯搬移，不改业务逻辑、不改 SQL 语义、不改 @Scheduled/线程模型、不改 @ConditionalOnProperty 条件、不顺手重构。

## 分阶段任务清单

### 阶段 1：MyBatis DAO+XML 改造 + 主类落位（1 个 commit）
1. 主类迁移：→ `org.cmb.TeamCoordinatorApplication`，加 scanBasePackages + @MapperScan（mapper 在 org.cmb 下，必须本阶段切换扫描）
2. application.yml / application-test.yml 加 mybatis 配置段
3. 新建 11 个 mapper 接口 + 11 个 XML（ExecutionMapper 先行试点并跑执行类集成测试验证映射正确，再铺开其余 10 个：Project/HumanRequest/MessageEvent/Artifact/Prompt/Skill/CoordinatorAgentRun/ConversationTask/IntentAnalysis/WorkspaceMapper）
4. 10 个门面迁至 `org.cmb.infrastructure.persistent`，注入 mapper，删内联 SQL 与 lambda 行映射；@Transactional/JSON 序列化/编排保留
5. WorkspaceController → presentation/controller + 新建 WorkspaceService
6. 删除 persistence 6 类（MyBatisExecutor/DatabaseMapper/BoundSqlStatement/DynamicSqlProvider/MyBatisRow/MyBatisRowMapper）+ BoundSqlStatementTest
7. 测试同步：7 个 @SpringBootTest 改主类 import；3 个直连 Repository 测试仅改 import 包名
- 完成标准：`mvn verify` 全绿；`grep -r "MyBatisExecutor\|BoundSqlStatement" src/` 无残留

### 阶段 2：common + 领域模型 + DTO + 端口接口（1 个 commit）
1. 9 枚举 → common/enums；ApiException/PlanValidationException → common/exception；DigitalTeamProperties → common/config
2. 26 个领域/协议 POJO + 7 个端口接口 → application/domain（平铺）
3. View/Request DTO → application/dto
4. 波及更新：阶段 1 的 persistent 门面 + WorkspaceService + 仍在旧包的 9 个 Mock/Http 实现类改 import（实现体不动）
- 完成标准：`mvn verify` 全绿

### 阶段 3：基础设施层（1 个 commit）
1. 11 个 Http/Mock 客户端 + FileStore 实现 → infrastructure/remoteaccess
2. SingleExpertWorker + ProjectEventStreamHub → infrastructure/worker（@Scheduled/线程模型原样）
3. 5 个校验器（DecisionSchemaValidator/PlanSchemaValidator/PlanValidator/ExpertSelector/OutputSchemaProvider）→ application/service
4. 波及更新：引用方 import
- 完成标准：`mvn verify` 全绿；旧包剩余类全部属于"应用服务+表现层"

### 阶段 4：应用层服务 + 表现层（1 个 commit）
1. 8 个 @Service + CoordinatorAgentClient → application/service
2. 16 个 @RestController → presentation/controller；2 个 Filter → presentation/filter；ApiExceptionHandler → presentation/exception
3. 测试 import 同步（MockAgentCoreControllerTest、MockFileFlowTest 等）
- 完成标准：`mvn verify` 全绿；`grep -rn "org.cmb.teamcoordinator" src/` 零残留

### 阶段 5：删除旧包 + 收尾（1 个 commit）
1. `git rm -r src/main/java/org/cmb/teamcoordinator`（已空）+ 空目录清理
2. 全局 grep 确认零残留 → `mvn verify` 全绿 → commit

## 测试同步策略

| 测试 | 时机 | 动作 |
|---|---|---|
| BoundSqlStatementTest | 阶段 1 | 删除（测的是退役类） |
| 7 个 @SpringBootTest(classes=...) | 阶段 1 | 主类 import 改一处 |
| 3 个直连 Repository 的集成测试 | 阶段 1 | 仅 import 包名，方法签名/bean 名零变化 |
| 9 个 JdbcTemplate 断言测试 | 全程 | 零改动（对 DAO 实现免疫） |
| 13 个 unit 测试 | 对应生产类迁移的阶段 | 仅 import 更新 |
| 容器/真实 AgentCore IT | — | 门控，保持现状 |

## 风险与规避

1. **H2 兼容**：XML SQL 逐字照抄现有方言（已全绿）；`<foreach>/<if>` 是 MyBatis 编译期处理不产生新语法；ExecutionMapper 先行试点验证。
2. **扫描时机**：主类+@MapperScan 阶段 1 一次到位，此后无扫描变更。
3. **@Transactional**：一律留在门面，mapper 不加事务注解。
4. **条件 Bean**：9 个 @ConditionalOnProperty 的注解与条件表达式原样保留，只改包名。
5. **单对象返回陷阱**：多行取首行的查询保持 List 签名。
6. **代理键误映射**：显式列清单硬性约定，消除全部 SELECT *。
7. **迁移文件**：V10/V13/V6_1/V7_1 与 schema 一律不动。
8. **双包共存**：阶段 1-4 新旧包并存合法，禁止同名类跨 commit 并存（先建新、删旧、同 commit）。
9. **列标签大小写**：若 ExecutionMapper 试点发现 H2 列标签大小写与驼峰映射不匹配，在 XML 加显式别名兜底（试点阶段解决，不扩散）。
10. **纪律**：每阶段 `mvn verify` 全绿是 commit 硬门槛；checkstyle 仅 4 条机械规则，新文件注意无通配 import、无行尾空白。

## 验证方式

- 每阶段结束：`mvn verify`（59 测试 + checkstyle + JaCoCo）全绿 → git commit（直接落 main）
- 阶段 1 试点：ExecutionMapper 完成后先跑 `mvn test -Dtest=SingleExpertExecutionIntegrationTest,ExecutionFaultToleranceIntegrationTest,MultiExpertExecutionIntegrationTest` 验证映射与事务正确
- 阶段 5 收尾：`grep -rn "teamcoordinator" src/` 零残留

## 阶段 6：DO 层 + 接口/实现拆分（2026-08-21）

背景：用户指出三处不符合 Java 规范 —— (1) application/domain 混装接口与类；(2) 没有 DO 定义；(3) 接口与实现类不在同一父目录。

### 决策

- **DO 层**：新建 `application/domain/entity/`，每张表一个 `XxxDO`（22 张表全量）。原 domain 行类改名迁入（ProjectRecord→ProjectDO、TaskRecord→TaskDO 等）；3 个兼任行类型的 DTO 迁入（ConversationTaskView→ConversationDO、PromptTemplateView→PromptTemplateDO、MessageAcceptedResponse→MessageDO）；2 个 Repository 嵌套 Record 提取为 HumanRequestDO/ArtifactDO；9 张只写/标量读取表建 definition-only DO（record 类，作为行形状的权威定义，mapper 标量参数签名不变）。包名最初用 `do` 被否决——`do` 是 Java 保留关键字、包名段非法（类名 `XxxDO` 不受影响），最终定为 `entity`。
- **Service 接口化**：9 个业务 @Service 拆成 `application/service` 接口 + `application/service/impl/XxxServiceImpl` 实现；@Service/@Transactional 全部落在 impl，接口零注解。PromptService 的 6 个模板 key 常量上移到接口（3 处 `PromptService.X` 调用点零改动）。
- **端口归位**：5 个端口接口（AgentCoreAdapter/FileStore/ExpertRegistry/IdentityProvider/IntentModelClient）移入 `application/service`，7 个实现移入 `application/service/impl`，`infrastructure/remoteaccess` 包删除。
- **辅助组件**：6 个具体类组件（CoordinatorAgentClient、PlanValidator/PlanSchemaValidator/DecisionSchemaValidator、ExpertSelector、OutputSchemaProvider）移入 `application/component`——无抽象边界，既非端口也非业务服务。
- **契约查询保持 Map**：findPlans/findTasks/findTaskDetail/findArtifacts/findConversation/listExpertSessions/findEvents/findMessages/findHumanRequests 与 WorkspaceService 不动——列别名即前端/CliSubmission 契约，转 DO 需 re-key 层，契约双写有漂移风险。

### 完成标准

- `mvn test` 60 测试全绿（surefire 含 *IntegrationTest 类）；`mvn verify` 在 Docker 可用时含 Testcontainers *IT 全绿
- `grep -rn "remoteaccess|MyBatisExecutor|MyBatisRow|HumanRequestRecord|ArtifactRecord" src/` 零残留
- dev 环境 workspace / CLI task-detail JSON 与重构前逐字节一致（/tmp/tc-baseline-*.json diff）
