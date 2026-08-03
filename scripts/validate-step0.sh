#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://127.0.0.1:8080}"

submit() {
  curl -fsS -X POST "${base_url}/mock/agentcore/runs" \
    -H 'Content-Type: application/json' \
    -d "$1"
}

success_session="$(submit '{"expertId":"expert-analysis","taskText":"analyze","idempotencyKey":"step0-success"}' | jq -r .sessionId)"
curl -fsS -H 'Content-Type: application/json' \
  "${base_url}/mock/agentcore/runs/${success_session}/streamEvents" |
  grep -q 'RUN_SUCCEEDED'

failed_session="$(submit '{"expertId":"expert-analysis","taskText":"fail","idempotencyKey":"step0-fail"}' | jq -r .sessionId)"
curl -fsS "${base_url}/mock/agentcore/runs/${failed_session}" |
  jq -e '.status == "FAILED"' >/dev/null

cancelled_session="$(submit '{"expertId":"expert-analysis","taskText":"long task","idempotencyKey":"step0-cancel"}' | jq -r .sessionId)"
curl -fsS -X POST "${base_url}/mock/agentcore/runs/${cancelled_session}/cancel" |
  jq -e '.status == "CANCELLED"' >/dev/null

echo "Step 0 local Mock validation passed."
