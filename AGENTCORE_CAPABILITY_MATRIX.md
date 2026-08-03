# AgentCore Capability Matrix

This matrix defines the local MVP contract. It is validated against the in-process
Mock and must be checked again against the real AgentCore before the remote adapter
is enabled.

| Capability | Local Mock Contract | Real Platform |
|---|---|---|
| Submit run | `POST /mock/agentcore/runs`, returns HTTP 202 and `sessionId` | Configurable HTTP adapter implemented; pending endpoint verification |
| Select expert | JSON field `expertId` | Pending verification |
| Coordinator agent | `expertId=coordinator`; returns a structured `decision` in the success payload | Agent ID configurable with `COORDINATOR_AGENT_ID`; pending verification |
| Task input | `taskText`, `structuredInput`, `attachmentRefs` | Pending verification |
| Run identity | Unique `mock-run-{uuid}`; retained for process lifetime | Pending verification |
| Query run | `GET /mock/agentcore/runs/{sessionId}` | Configurable path implemented; pending verification |
| Stream events | GET with JSON request headers; response is `text/event-stream` | SSE parser and cursor implemented; pending verification |
| Event order | Monotonic `sequence`; consumers sort and deduplicate by sequence | Pending verification |
| Success | Terminal `RUN_SUCCEEDED` / `SUCCEEDED` | Pending verification |
| Failure | A task containing `fail` ends as `RUN_FAILED` / `FAILED` | Pending verification |
| Timeout | A task containing `timeout` ends as `RUN_TIMED_OUT` / `TIMED_OUT` | Pending verification |
| Cancel | `POST /mock/agentcore/runs/{sessionId}/cancel` | Configurable path implemented; pending verification |
| Idempotency | Same non-empty `idempotencyKey` returns the same session | Pending verification |
| Pause/resume | Not supported; submit a new Run with a new idempotency key and prior context | Pending verification |
| Restart recovery | Mock runs are process-local; Coordinator run identity and cursor are durable in MySQL | Pending verification |
| Experts | Analysis, writing and file-processing experts | Map to test expert IDs |
| Expert state | `enabled`, `available`, `concurrencyLimit`, `capabilities` | Pending verification |
| Upload | Reserve URL with `/mock/files/presign`, then PUT content | Pending verification |
| Download | Descriptor and binary content endpoints | Pending verification |
| File limits | 10 MiB; any content type in local Mock | Pending verification |
| Metadata | File name, content type, size and SHA-256 checksum | Pending verification |
| Access control | None in Step 0 Mock; project authorization is introduced in Step 2 | Pending verification |
| Cleanup | In-memory data is removed when the process exits | Pending verification |
| Expert artifact | Successful Run creates a downloadable `result.txt` | Pending verification |

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
