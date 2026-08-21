# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development

- **Java 21** required; Maven enforces this with `maven-enforcer-plugin`.
- Build: `mvn compile`
- Run all tests (unit + integration): `mvn verify`
- Run a single test class: `mvn test -Dtest=ClassName`
- Run a single test method: `mvn test -Dtest=ClassName#methodName`
- Checkstyle runs during `verify` (`mvn verify`). Config at `checkstyle.xml`.
- JaCoCo coverage reports generated during `verify` phase.
- Integration tests use the `*IT.java` suffix (run by `maven-failsafe-plugin`), unit tests use `*Test.java`.
- Start local dependencies: `docker compose up -d` (MySQL 8.0, Redis 7, MinIO).
- Local profile (`application-local.yml`) is the default; keep it in `.gitignore` for secrets.

## Architecture

TeamCoordinator is a **Spring Boot 2.7** service that acts as an AI agent orchestrator. It receives user messages, analyzes intent via an AI Coordinator agent, creates execution plans, dispatches tasks to expert AI agents through an AgentCore abstraction, and streams results back to clients via SSE.

### Core Flow

```
User Message → Intent Analysis (Coordinator Agent) → Planning → Expert Execution → SSE Events
                    ↕                                      ↕
             digital_team_coordinator_agent_run      digital_team_coordinator_task
             digital_team_coordinator_dispatch       digital_team_coordinator_plan
```

1. **Message intake**: `POST /api/v1/projects/{projectId}/tasks/{taskId}/messages` → `CoordinatorMessageService` inserts a message and dispatch record.
2. **Dispatch worker**: `SingleExpertWorker.runOnce()` (scheduled, 500ms default) claims the next pending dispatch via a database lease.
3. **Intent analysis**: The worker calls `IntentAnalysisService`, which delegates to `CoordinatorAgentClient` — this submits the user text to the Coordinator AI agent via `AgentCoreAdapter`, streams SSE events, and extracts a `CoordinatorDecision` (ANSWER, ASK_HUMAN, or CREATE_PLAN).
4. **Planning**: For CREATE_PLAN decisions the worker parses the coordinator output into a `CoordinatorPlanSpec` (tasks, dependencies, capability requirements), validates it (`PlanValidator`, `PlanSchemaValidator`, up to 2 repair attempts) and persists the plan via `ExecutionRepository.createPlan`.
5. **Task execution**: `SingleExpertWorker` picks an expert via `ExpertSelector`, submits each task to AgentCore, and streams events. Tasks respect dependency order. Failed tasks get one automatic correction attempt.
6. **Event delivery**: Results are written as `ProjectEvent` rows and pushed to SSE subscribers via `ProjectEventStreamHub`. SSE connections for a task poll MySQL periodically to catch events written by other Coordinator instances.

### Key Abstractions

- **`AgentCoreAdapter`** (interface in `application/service/`, impls in `application/service/impl/`): Interface for submitting runs to AI agents, streaming events, cancelling, and resuming. Two implementations:
  - `MockAgentCoreAdapter` (default): In-process mock returning simulated agent runs.
  - `HttpAgentCoreAdapter`: Real HTTP adapter (`@ConditionalOnProperty` mock-enabled=false), communicates with AgentCore via REST + SSE.
- **Submission tools** (`AgentCoreTools` + `agentcore-tools/` resources): `submit_coordinator_decision` / `submit_coordinator_plan` / `submit_review_verdict` are attached to agents on the AgentCore side; agents submit structured output as tool calls. Consumers read the `toolUsed` event's `input` first and fall back to the `end` event content (agents without the tool attached keep working).
- **Persistence** (`infrastructure/persistent/`): **No JPA/Hibernate** — repositories compose parameterized SQL in one-mapper-per-table `@Mapper` interfaces + `resources/mapper/*.xml`, mapping rows via MyBatis resultMaps to DO row types in `application/domain/entity/` (`XxxDO`). Workspace/CLI snapshot queries intentionally return `Map<String,Object>` whose column aliases are the front-end JSON contract.
- **`ProjectEventStreamHub`** (`infrastructure/worker/`): Manages SSE emitters per (tenant, project, task). Publishes events in-memory for same-instance subscribers, and polls MySQL for cross-instance event delivery. Supports `Last-Event-ID` replay.
- **`SingleExpertWorker`** (`infrastructure/worker/`): The main orchestration loop. Claims dispatches with a database lease (`ExecutionRepository.claimNext`), runs intent analysis, creates plans, dispatches tasks to experts, and consumes their events.
- **`IntentAnalysisService`** (`application/service/`) / **`CoordinatorAgentClient`** (`application/component/`): Wrap the Coordinator agent run lifecycle — submit, stream, parse JSON decision output, optionally repair invalid output, and persist the run state.
- **`OutputSchemaProvider`** (`application/component/`): Loads the CoordinatorDecision / CoordinatorPlan JSON Schemas so they can be injected into prompts (`{{output_schema}}` in prompt template v2+), making the output contract explicit at the source.
- **Identity contract**: `X-Tenant-Id` + `X-User-Id` headers (`HeaderIdentityProvider`). Project authorization is role-based (OWNER, ADMIN, MEMBER, VIEWER).

### Package Map

| Package | Responsibility |
|---|---|
| `application/domain` | Value/protocol objects (`AgentEvent`, `CoordinatorDecision`, `AgentRunRequest`...); `domain/entity/` subpackage holds one DO (`XxxDO`) per table |
| `application/dto` | API request/response DTOs |
| `application/service` | Interfaces only: 9 business services + 5 ports (`AgentCoreAdapter`, `FileStore`, `ExpertRegistry`, `IdentityProvider`, `IntentModelClient`) |
| `application/service/impl` | All interface implementations: `XxxServiceImpl` + Http/Mock adapters, file stores, expert registry, identity provider |
| `application/component` | Concrete collaborator components: `CoordinatorAgentClient`, `PlanValidator`/`PlanSchemaValidator`/`DecisionSchemaValidator`, `ExpertSelector`, `OutputSchemaProvider` |
| `common/` | `DigitalTeamProperties` (`@ConfigurationProperties`), enums, `ApiException` |
| `infrastructure/persistent` | Repository facades + `mapper/` (one `@Mapper` interface per table) + `typehandler/` |
| `infrastructure/worker` | `ProjectEventStreamHub` (SSE hub), `SingleExpertWorker` (dispatch loop) |
| `presentation/` | `controller/` REST endpoints, `exception/` global handler, `filter/` (`MvpFeatureFlagFilter`, `TraceContextFilter`) |

### Database

- MySQL via Flyway migrations (Java-based in `db/migration/` and SQL-based in `resources/db/migration/`).
- **Runtime schema init/upgrade path is `db/init/*.sql`** (compose mounts them into MySQL; `03-upgrade.sql` is idempotent — rename pass + column/index convergence — safe to re-run). Flyway is disabled by default (`FLYWAY_ENABLED=false`); do not enable it against a DB already migrated by `db/init` — checksums/table names diverged after the `digital_team_` prefix rename.
- Convention: `databaseId` is the auto-increment primary key; `businessId` is the external-facing UUID. API surfaces only business IDs.
- Key tables (all prefixed with `digital_team_`): `digital_team_project`, `digital_team_project_member`, `digital_team_project_expert`, `digital_team_project_message`, `digital_team_project_event`, `digital_team_coordinator_dispatch`, `digital_team_coordinator_agent_run`, `digital_team_coordinator_plan`, `digital_team_coordinator_task`, `digital_team_prompt_template`, `digital_team_prompt_execution`.
- Mapper XMLs follow one-file-per-table: each `resources/mapper/*.xml` + matching interface in `.../persistent/mapper/` is named after its table; JOIN queries live in the main table's XML.
- Connection pooling via HikariCP (max 10 connections).

### Configuration

All runtime behavior is configured through `DigitalTeamProperties` (`digital-team.*` prefix in `application.yml`). Key env vars:
- `AGENTCORE_MOCK_ENABLED` (default `true`): Use in-process mock vs. real AgentCore HTTP calls.
- `AGENTCORE_BASE_URL`, `AGENTCORE_AUTH_VALUE`: Required when mock is disabled.
- `EXECUTION_WORKER_INTERVAL_MS` (default `500`): Polling interval for the dispatch worker.
- `EVENT_DATABASE_POLL_INTERVAL_MS` (default `500`): SSE-to-database sync interval.
- `DIGITAL_TEAM_MVP_ENABLED` / `DIGITAL_TEAM_EMERGENCY_STOP`: Feature flag filter on `/api/v1/*`.
- `AGENTCORE_ARTIFACT_TOOL_TOKEN`: Shared secret for AgentCore's file upload tool.

### Testing

- Unit tests in `src/test/java/.../unit/`. Integration tests in `.../integration/`.
- Integration tests use Testcontainers (MySQL, Redis) or H2 for lightweight tests.
- `InfrastructureContainersIT` provides shared container lifecycle.
- `RealAgentCoreAcceptanceIT` is gated behind `AGENTCORE_REAL_TEST_ENABLED=true` and requires a live AgentCore instance.
- Test config (`application-local.yml`) with H2 datasource is used when running tests without containers.

### Local Mock Endpoints

When running with the default local profile, `MockAgentCoreController` and `MockFileController` provide in-process stubs at `/mock/agentcore/*` and `/mock/files/*` — no external AgentCore needed.
