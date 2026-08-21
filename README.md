# TeamCoordinator

## Persistence

Application database access uses MyBatis. Controllers and services depend on
domain repositories; repositories execute parameterized SQL through one
mapper interface per table (`@Mapper` in
`org.cmb.infrastructure.persistent.mapper`) with XML statements in
`src/main/resources/mapper/`.

- Runtime values are bound with MyBatis `#{}` parameters.
- Rows are mapped through resultMaps to DO row types
  (`org.cmb.application.domain.entity.XxxDO`, one per table).
- Workspace/CLI snapshot queries return `Map<String,Object>` whose column
  aliases are the front-end JSON contract.
- Database `id` maps to `databaseId`; `business_id` maps to `businessId`.
- API identifiers expose business IDs only.
- `JdbcTemplate` is limited to integration-test assertions and is not used by
  production data access.

Current business, SSE, AgentCore, and local mock contracts are documented in
[`API_DOCUMENTATION.md`](API_DOCUMENTATION.md).

MVP Spring Boot skeleton for the digital team coordinator service.

The required build and runtime is JDK 21. Maven rejects other JDK major
versions so local and CI builds use the same Java baseline.

## Prompt Management

Coordinator and expert Prompts are versioned in MySQL. Runtime calls use only
the `PUBLISHED` version and record the rendered Prompt and variables in
`digital_team_prompt_execution`. Manage versions through `/api/v1/admin/prompts`; configure
administrators with `PROMPT_ADMIN_USERS` as comma-separated user IDs.

Coordinator execution/planning and expert execution/resume use separate
templates and context builders. Templates use `{{context_json}}`; Prompt text
is no longer loaded from classpath `.txt` files.

## Local Dependencies

The default `local` profile connects to MySQL at `127.0.0.1:3306/xservice`
and Redis at `127.0.0.1:6379`, and uses MinIO at `127.0.0.1:9000`.
Keep credentials in the ignored
`src/main/resources/application-local.yml`, or provide `MYSQL_USERNAME`,
`MYSQL_PASSWORD`, `MYSQL_URL`, `REDIS_HOST`, `REDIS_PORT`, and
`REDIS_PASSWORD` environment variables.

Use `docker compose up -d` when these dependencies are not already running
locally. The Compose credentials are development defaults and should be
overridden through environment variables.

## Local Mock Contract

The local profile uses in-process mocks for AgentCore and experts because local development does not match the actual runtime environment.

- `GET /health`
- `GET /ready`
- `GET /mock/experts`
- `POST /mock/agentcore/runs`
- `GET /mock/agentcore/runs/{sessionId}`
- `GET /mock/agentcore/runs/{sessionId}/streamEvents`
- `POST /mock/agentcore/runs/{sessionId}/cancel`
- `POST /mock/files/presign`
- `PUT /mock/files/{fileId}/content`
- `GET /mock/files/{fileId}`
- `GET /mock/files/{fileId}/content`

`POST /mock/agentcore/runs` accepts JSON and returns `202 Accepted` with a `sessionId`.

`streamEvents` accepts ordinary JSON request headers from callers, but responds with `Content-Type: text/event-stream`.

See `AGENTCORE_CAPABILITY_MATRIX.md` for the validated mock contract and the
remaining real-platform verification work.

## Project API

Project endpoints use `X-Tenant-Id` and `X-User-Id` as the local identity
contract. The adapter will be replaced by X Identity Service in the deployed
environment. OpenAPI JSON is available at `/v3/api-docs` and Swagger UI at
`/swagger-ui.html`.

Create each new conversation as a business Task with
`POST /api/v1/projects/{projectId}/tasks`. The response contains the Task's
stable business `sessionId`. Messages are submitted with
`POST /api/v1/projects/{projectId}/tasks/{taskId}/messages`, and Task events
are streamed from `GET /api/v1/projects/{projectId}/tasks/{taskId}/events`.
The endpoint supports the `Last-Event-ID` header for replay.

Each Coordinator instance polls MySQL only for Tasks with local SSE
subscribers. This lets an SSE connection receive events written by another
Coordinator instance without Redis Pub/Sub or Streams. The polling interval
defaults to 500 ms and can be changed with
`EVENT_DATABASE_POLL_INTERVAL_MS`.

## Single Expert Execution

The message outbox is consumed by a database-leased worker. Intent analysis is
itself submitted to the Coordinator agent through AgentCore. Its `session_id`,
SSE cursor, input snapshot, and repair stage are stored in
`digital_team_coordinator_agent_run`. After a valid decision is returned, expert tasks use
the same submit-and-stream protocol. Another Coordinator instance can take
over either phase after the dispatch lease expires.

The local worker interval defaults to 500 ms and can be changed with
`EXECUTION_WORKER_INTERVAL_MS`. Tasks can be cancelled with
`DELETE /api/v1/projects/{projectId}/expert-tasks/{expertTaskId}`.

The business Task `sessionId` remains Coordinator-owned context. AgentCore's
returned `sessionId` is separate and is used for stream queries, stop requests,
and human answers for that individual run.

### Real AgentCore

Set `AGENTCORE_MOCK_ENABLED=false` and provide:

- `AGENTCORE_BASE_URL`
- `AGENTCORE_AUTH_HEADER` and `AGENTCORE_AUTH_VALUE`
- `AGENTCORE_ARTIFACT_TOOL_TOKEN`
- `COORDINATOR_AGENT_ID` (defaults to `coordinator`)
- `AGENTCORE_SESSION_HEADER` (defaults to `X-Session-Id`)
- Optional `AGENTCORE_SUBMIT_PATH`, `AGENTCORE_STATUS_PATH`,
  `AGENTCORE_STREAM_PATH`, and `AGENTCORE_CANCEL_PATH`

Register the AgentCore HTTP file tool `upload_artifact` against
`POST /api/v1/agent-tools/projects/{projectId}/tasks/{taskId}/artifacts`.
AgentCore sends the Agent-generated file as multipart field `file` and injects
the configured tool token, business session, Agent run ID, and Agent ID.
Coordinator stores the bytes in MinIO and returns an `artifactId`; neither the
Agent nor AgentCore needs MinIO credentials.

The HTTP adapter sends JSON request headers and accepts
`text/event-stream` from the stream endpoint. To run the gated 20-run
acceptance suite:

```bash
AGENTCORE_REAL_TEST_ENABLED=true \
AGENTCORE_BASE_URL=https://agentcore.example/api \
AGENTCORE_AUTH_VALUE="Bearer replace-me" \
AGENTCORE_TEST_EXPERT_ID=replace-me \
mvn verify
```

The real acceptance test is skipped unless
`AGENTCORE_REAL_TEST_ENABLED=true`.
