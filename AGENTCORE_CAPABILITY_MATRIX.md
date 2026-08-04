# AgentCore Capability Matrix

This matrix defines the local MVP contract. It is validated against the in-process
Mock and must be checked again against the real AgentCore before the remote adapter
is enabled.

| Capability | Local Mock Contract | Real Platform |
|---|---|---|
| Submit run | `POST /mock/agentcore/runs`, real `userInput` envelope | Request/response schema implemented |
| Coordinator/expert routing | Adapter-selected AgentCore endpoint/configuration; no `expertId` wire field | Endpoint routing still requires deployment configuration |
| Task input | `systemPrompt`, contents, hidden context, skills and downloadable attachments | Schema implemented |
| Run identity | Response `data.sessionId` and `conversationId` | Schema implemented |
| Business session | One stable Coordinator session per conversation Task; separate from AgentCore run session | Implemented |
| Query run | `GET /mock/agentcore/runs/{sessionId}` | Configurable path implemented; pending verification |
| Stream events | GET with JSON request headers; response is `text/event-stream` | Supplied raw event formats parsed |
| Event identity | Every chunk has top-level `eventId`; Coordinator assigns local ordering | Deduplication by `eventId` implemented |
| Success | Terminal `RUN_SUCCEEDED` / `SUCCEEDED` | Pending verification |
| Failure | A task containing `fail` ends as `RUN_FAILED` / `FAILED` | Pending verification |
| Timeout | A task containing `timeout` ends as `RUN_TIMED_OUT` / `TIMED_OUT` | Pending verification |
| Cancel | `stopSession` sent to the conversation endpoint | Implemented |
| Idempotency | Coordinator-owned keys remain in MySQL; absent from AgentCore payload | Implemented |
| Pause/resume | `userAnswerQuestion` with AgentCore `questionId` and answer map | Implemented |
| Restart recovery | Mock runs are process-local; Coordinator run identity and cursor are durable in MySQL | Pending verification |
| Experts | Analysis, writing and file-processing experts | Map to test expert IDs |
| Expert state | `enabled`, `available`, `concurrencyLimit`, `capabilities` | Pending verification |
| Upload | Reserve URL with `/mock/files/presign`, then PUT content | Pending verification |
| Download | Descriptor and binary content endpoints | Pending verification |
| File limits | 10 MiB; any content type in local Mock | Pending verification |
| Metadata | File name, content type, size and SHA-256 checksum | Pending verification |
| Access control | None in Step 0 Mock; project authorization is introduced in Step 2 | Pending verification |
| Cleanup | In-memory data is removed when the process exits | Pending verification |
| Expert artifact | Registered `upload_artifact` multipart tool returns an `artifactId`; terminal Run references it through `artifactIds` | Mock integration verified; real AgentCore registration pending |

## Event Sample

```text
id: 1
event: RUN_ACCEPTED
data: {"sessionId":"mock-run-...","sequence":1,"status":"ACCEPTED"}

id: 2
event: RUN_PROGRESS
data: {"sessionId":"mock-run-...","sequence":2,"status":"RUNNING"}

id: 3
event: RUN_SUCCEEDED
data: {"sessionId":"mock-run-...","sequence":3,"status":"SUCCEEDED"}
```

## Known Risks And Owners

| Risk | Owner | Target |
|---|---|---|
| Real endpoint paths, authentication and request schema differ from Mock | AgentCore integration owner (must be assigned) | Before enabling the implemented remote adapter |
| Real SSE replay, duplicate delivery and reconnect cursor semantics are unknown | AgentCore integration owner (must be assigned) | Before Step 5 |
| Real pause/resume and restart recovery semantics are unknown | AgentCore integration owner (must be assigned) | Before Step 7 |
| Object storage signing, ACL, limits and retention are unknown | Storage integration owner (must be assigned) | Before Step 8 |

G0 remains open until named owners and calendar dates replace the placeholders
above and the real-platform column is verified.
