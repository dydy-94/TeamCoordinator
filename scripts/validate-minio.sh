#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://127.0.0.1:8080}"
input_text="handoff through minio"

reservation="$(curl -fsS -X POST "${base_url}/mock/files/presign" \
  -H 'Content-Type: application/json' \
  -d '{"fileName":"step0-input.txt","contentType":"text/plain"}')"
input_id="$(jq -r .fileId <<<"${reservation}")"
upload_url="$(jq -r .uploadUrl <<<"${reservation}")"

curl -fsS -X PUT "${upload_url}" \
  -H 'Content-Type: text/plain' \
  --data-binary "${input_text}" >/dev/null

run="$(curl -fsS -X POST "${base_url}/mock/agentcore/runs" \
  -H 'Content-Type: application/json' \
  -d "{\"expertId\":\"expert-file\",\"taskText\":\"process minio input\",\"attachmentRefs\":[\"${input_id}\"]}")"
session_id="$(jq -r .sessionId <<<"${run}")"
status="$(curl -fsS "${base_url}/mock/agentcore/runs/${session_id}")"

jq -e --arg expected "${input_text}" \
  '.status == "SUCCEEDED" and .payload.attachmentContents[0] == $expected' \
  <<<"${status}" >/dev/null

artifact_url="$(jq -r '.payload.artifactRefs[0]' <<<"${status}")"
artifact_id="$(jq -r '.payload.artifactFileIds[0]' <<<"${status}")"
artifact="$(curl -fsS "${artifact_url}")"
grep -q "${input_text}" <<<"${artifact}"

curl -fsS -X DELETE "${base_url}/mock/files/${input_id}" >/dev/null
curl -fsS -X DELETE "${base_url}/mock/files/${artifact_id}" >/dev/null

echo "MinIO attachment and expert artifact validation passed."
