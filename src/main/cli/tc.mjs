#!/usr/bin/env node
/**
 * tc — TeamCoordinator companion CLI.
 *
 * Runs on the AgentCore machine. Agents submit their structured Coordinator
 * outputs (decision / plan / verdict) and generated artifacts directly to
 * TeamCoordinator, instead of relying on streamed-text parsing.
 *
 * Zero dependencies (Node 18+, built-in fetch/fs).
 *
 * Configuration (environment, set at install time):
 *   TC_BASE_URL  TeamCoordinator base URL, e.g. http://127.0.0.1:8080
 *   TC_TOKEN     Shared secret (matches AGENTCORE_ARTIFACT_TOOL_TOKEN)
 *
 * Per-run context is passed as command flags, never via environment:
 * the task id is the only identifier shared by Coordinator, AgentCore
 * and the CLI.
 *
 * Usage:
 *   tc submit-decision --task <conversationTaskId> [--file out.json | stdin]
 *   tc submit-plan     --task <conversationTaskId> [--file out.json | stdin]
 *   tc submit-verdict  --task <conversationTaskId> [--file out.json | stdin]
 *   tc get-task        --task <coordinatorTaskId>
 *   tc get-artifact    --task <coordinatorTaskId> [--name <fileName>]
 *                                    [--output <path>]
 *   tc submit-result   --task <coordinatorTaskId> [--file out.txt | --text "..." | stdin]
 *   tc ask-human       --task <coordinatorTaskId> [--question "..." | stdin]
 *   tc upload-artifact <file-path> --task <coordinatorTaskId>
 *   tc validate decision|plan|verdict [--file f.json | stdin]
 *   tc health
 *
 * Exit code 0 on success, 1 on validation or transport failure.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));

const VERSION = "1.0.0";

const DECISION_TYPES = ["ANSWER", "ASK_HUMAN", "CREATE_PLAN"];
const RISK_LEVELS = ["LOW", "MEDIUM", "HIGH"];
const EXECUTION_MODES = ["SINGLE_EXPERT", "MULTI_EXPERT"];
const TASK_INTENT_REQUIRED = [
  "intent", "objective", "expected_outputs", "constraints",
  "required_capabilities", "input_refs", "missing_information",
  "risk_level", "execution_mode",
];
const PLAN_TASK_REQUIRED = [
  "task_key", "objective", "dependencies", "expected_output",
  "acceptance_criteria", "required_capabilities",
];

function fail(message) {
  console.error(`tc: ${message}`);
  process.exit(1);
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value || !value.trim()) {
    fail(`environment variable ${name} is not set`);
  }
  return value.trim();
}

/**
 * Per-run context values are passed as command flags (the agent does not
 * have per-run environment injection available). Optional values fall
 * back to "" so optional server-side filters can be skipped.
 */
function flagValue(name) {
  const index = process.argv.indexOf(`--${name}`);
  return index === -1 ? "" : (process.argv[index + 1] || "");
}

function requireFlag(name) {
  const value = flagValue(name);
  if (!value || !value.trim()) {
    fail(`missing required flag --${name}`);
  }
  return value.trim();
}

function baseUrl() {
  return requireEnv("TC_BASE_URL").replace(/\/+$/, "");
}

function authHeaders() {
  return { "X-AgentCore-Tool-Token": requireEnv("TC_TOKEN") };
}

function jsonHeaders() {
  return {
    "Content-Type": "application/json",
    ...authHeaders(),
  };
}

function readPayload(command) {
  const fileIndex = process.argv.indexOf("--file");
  if (fileIndex !== -1) {
    const path = process.argv[fileIndex + 1];
    if (!path) {
      fail(`${command} --file requires a path`);
    }
    try {
      return readFileSync(path, "utf8");
    } catch (ex) {
      fail(`cannot read file ${path}: ${ex.message}`);
    }
  }
  return readFileSync(0, "utf8");
}

function parseJson(command, raw) {
  try {
    return JSON.parse(raw);
  } catch (ex) {
    fail(`${command}: input is not valid JSON: ${ex.message}`);
  }
}

// ── Local validation (mirrors the server-side schemas) ────────────────

function isString(v) {
  return typeof v === "string" && v.trim().length > 0;
}

function isStringArray(v) {
  return Array.isArray(v) && v.every((item) => typeof item === "string");
}

function validateDecision(node) {
  if (node === null || typeof node !== "object" || Array.isArray(node)) {
    fail("decision must be a JSON object");
  }
  if (!DECISION_TYPES.includes(node.decision_type)) {
    fail(`decision_type must be one of ${DECISION_TYPES.join("/")}`);
  }
  if (node.decision_type === "ANSWER" && !isString(node.answer)) {
    fail("ANSWER decisions require a non-empty answer");
  }
  if (node.decision_type === "ASK_HUMAN" && !isString(node.question)) {
    fail("ASK_HUMAN decisions require a non-empty question");
  }
  if (node.decision_type === "CREATE_PLAN") {
    const intent = node.task_intent;
    if (intent === null || typeof intent !== "object") {
      fail("CREATE_PLAN decisions require a task_intent object");
    }
    for (const field of TASK_INTENT_REQUIRED) {
      if (intent[field] === undefined || intent[field] === null) {
        fail(`task_intent.${field} is required`);
      }
    }
    if (!isString(intent.intent) || !isString(intent.objective)) {
      fail("task_intent.intent and task_intent.objective must be non-empty strings");
    }
    for (const field of [
      "expected_outputs", "constraints", "required_capabilities",
      "input_refs", "missing_information",
    ]) {
      if (!isStringArray(intent[field])) {
        fail(`task_intent.${field} must be an array of strings`);
      }
    }
    if (!RISK_LEVELS.includes(intent.risk_level)) {
      fail(`task_intent.risk_level must be one of ${RISK_LEVELS.join("/")}`);
    }
    if (!EXECUTION_MODES.includes(intent.execution_mode)) {
      fail(`task_intent.execution_mode must be one of ${EXECUTION_MODES.join("/")}`);
    }
  }
}

function validatePlan(node) {
  if (node === null || typeof node !== "object" || Array.isArray(node)) {
    fail("plan must be a JSON object");
  }
  if (typeof node.plan_version !== "number" || node.plan_version < 1) {
    fail("plan_version must be an integer >= 1");
  }
  const tasks = node.tasks;
  if (!Array.isArray(tasks) || tasks.length < 1 || tasks.length > 8) {
    fail("tasks must be an array of 1..8 items");
  }
  const keys = new Set();
  for (const task of tasks) {
    if (task === null || typeof task !== "object") {
      fail("each task must be an object");
    }
    for (const field of PLAN_TASK_REQUIRED) {
      if (task[field] === undefined || task[field] === null) {
        fail(`task.${field} is required`);
      }
    }
    if (!isString(task.task_key) || !isString(task.objective)) {
      fail("task.task_key and task.objective must be non-empty strings");
    }
    if (!isStringArray(task.dependencies)
        || !isStringArray(task.required_capabilities)) {
      fail("task.dependencies and task.required_capabilities must be string arrays");
    }
    if (task.required_capabilities.length < 1) {
      fail("task.required_capabilities must have at least one entry");
    }
    if (!isString(task.expected_output) || !isString(task.acceptance_criteria)) {
      fail("task.expected_output and task.acceptance_criteria must be non-empty strings");
    }
    if (keys.has(task.task_key)) {
      fail(`duplicate task_key: ${task.task_key}`);
    }
    keys.add(task.task_key);
  }
  for (const task of tasks) {
    for (const dep of task.dependencies) {
      if (!keys.has(dep)) {
        fail(`unknown dependency ${dep} in task ${task.task_key}`);
      }
    }
  }
}

function validateVerdict(node) {
  if (node === null || typeof node !== "object" || typeof node.consistent !== "boolean") {
    fail("verdict must be an object with a boolean \"consistent\" field");
  }
}

// ── Help ──────────────────────────────────────────────────────────────

const COMMAND_HELP = {
  "submit-decision": {
    title: "提交协调者决策（ANSWER / ASK_HUMAN / CREATE_PLAN）",
    usage: "tc submit-decision --task <会话taskId> [--file decision.json | stdin]",
    flags: [
      "--task <id>    会话 taskId（必填，取自提示词 coordinator_context.conversation_task_id）",
      "--file <path>  决策 JSON 文件路径（省略则从 stdin 读取）",
    ],
    examples: [
      "tc submit-decision --task task-123 --file decision.json",
      "cat decision.json | tc submit-decision --task task-123",
    ],
  },
  "submit-plan": {
    title: "提交执行计划（服务端校验后直写 plan + tasks）",
    usage: "tc submit-plan --task <会话taskId> [--file plan.json | stdin]",
    flags: [
      "--task <id>    会话 taskId（必填）",
      "--file <path>  计划 JSON 文件路径（省略则从 stdin 读取）",
    ],
    examples: ["tc submit-plan --task task-123 --file plan.json"],
  },
  "submit-verdict": {
    title: "提交语义审查结论 {\"consistent\": bool, \"reason\": \"...\"}",
    usage: "tc submit-verdict --task <会话taskId> [--file verdict.json | stdin]",
    flags: [
      "--task <id>    会话 taskId（必填）",
      "--file <path>  结论 JSON 文件路径（省略则从 stdin 读取）",
    ],
    examples: ["tc submit-verdict --task task-123 --file verdict.json"],
  },
  "get-task": {
    title: "拉取专家任务契约（渲染后的提示词 + 验收标准 + 附件列表）",
    usage: "tc get-task --task <协调taskId>",
    flags: ["--task <id>    协调任务 taskId（必填，派发时下发）"],
    examples: ["tc get-task --task task-456"],
  },
  "get-artifact": {
    title: "下载任务可用的产物（默认第一个附件）",
    usage: "tc get-artifact --task <协调taskId> [--name <文件名>] [--output <路径>]",
    flags: [
      "--task <id>    协调任务 taskId（必填）",
      "--name <name>  按文件名选择附件（省略则取第一个）",
      "--output <p>   保存路径（默认保存为原文件名）",
    ],
    examples: [
      "tc get-artifact --task task-456",
      "tc get-artifact --task task-456 --name spec.txt --output /tmp/spec.txt",
    ],
  },
  "submit-result": {
    title: "写回专家执行结果（result_json + SUCCEEDED）",
    usage: "tc submit-result --task <协调taskId> [--text \"...\" | --file result.txt | stdin]",
    flags: [
      "--task <id>    协调任务 taskId（必填）",
      "--text <t>     结果文本",
      "--file <path>  结果文本文件（与 --text 二选一；都省略则从 stdin 读取）",
    ],
    examples: ["tc submit-result --task task-456 --text \"分析完成，风险清单见附件\""],
  },
  "ask-human": {
    title: "向用户求助（任务 RUNNING → WAITING_HUMAN + 创建问题）",
    usage: "tc ask-human --task <协调taskId> [--question \"...\" | stdin]",
    flags: [
      "--task <id>      协调任务 taskId（必填）",
      "--question <q>   问题文本（省略则从 stdin 读取）",
    ],
    examples: ["tc ask-human --task task-456 --question \"需要接口清单，请上传\""],
  },
  "upload-artifact": {
    title: "上传产物文件（版本管理 + 依赖血缘）",
    usage: "tc upload-artifact --task <协调taskId> <文件路径>",
    flags: ["--task <id>    协调任务 taskId（必填）"],
    examples: ["tc upload-artifact --task task-456 result.pdf"],
  },
  "validate": {
    title: "仅本地校验（不发请求）",
    usage: "tc validate decision|plan|verdict [--file f.json | stdin]",
    flags: [
      "decision|plan|verdict   校验对象",
      "--file <path>           JSON 文件路径（省略则从 stdin 读取）",
    ],
    examples: ["tc validate plan --file plan.json"],
  },
  "health": {
    title: "连通性检查（GET /health）",
    usage: "tc health",
    flags: [],
    examples: ["tc health"],
  },
};

function printCommandHelp(command) {
  const help = COMMAND_HELP[command];
  if (!help) {
    fail(`unknown command "${command}" (use: tc -h)`);
  }
  console.log(`${help.title}`);
  console.log();
  console.log(`用法: ${help.usage}`);
  if (help.flags.length > 0) {
    console.log();
    console.log("参数:");
    for (const flag of help.flags) {
      console.log(`  ${flag}`);
    }
  }
  console.log();
  console.log("示例:");
  for (const example of help.examples) {
    console.log(`  ${example}`);
  }
}

function printOverview() {
  console.log(`tc ${VERSION} — TeamCoordinator 配套 CLI（taskId 键控）`);
  console.log();
  console.log("用法: tc <命令> [参数]");
  console.log("      tc -h                 本帮助");
  console.log("      tc <命令> -h          命令详细帮助");
  console.log("      tc -v / --version     版本");
  console.log();
  console.log("命令:");
  for (const [name, help] of Object.entries(COMMAND_HELP)) {
    console.log(`  ${name.padEnd(16)} ${help.title}`);
  }
  console.log();
  console.log("环境变量（安装时配置）:");
  console.log("  TC_BASE_URL  TeamCoordinator 地址（如 http://127.0.0.1:8080）");
  console.log("  TC_TOKEN     共享密钥（对应 AGENTCORE_ARTIFACT_TOOL_TOKEN）");
  console.log();
  console.log("退出码: 0 成功；1 校验失败或传输失败");
}

// ── Commands ───────────────────────────────────────────────────────────

async function submit(kind, endpoint) {
  const taskId = requireFlag("task");
  const raw = readPayload(`submit-${kind.toLowerCase()}`);
  const node = parseJson(`submit-${kind.toLowerCase()}`, raw);
  if (kind === "decision") {
    validateDecision(node);
  } else if (kind === "plan") {
    validatePlan(node);
  } else {
    validateVerdict(node);
  }
  const response = await fetch(`${baseUrl()}${endpoint}`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify({ task_id: taskId, payload: raw.trim() }),
  });
  if (!response.ok) {
    fail(`submit-${kind.toLowerCase()} rejected (HTTP ${response.status}): ${await response.text()}`);
  }
  console.log(`submit-${kind.toLowerCase()}: accepted`);
}

async function uploadArtifact() {
  const taskId = requireFlag("task");
  const filePath = process.argv[3];
  if (!filePath) {
    fail("upload-artifact requires a file path");
  }
  let content;
  try {
    content = readFileSync(filePath);
  } catch (ex) {
    fail(`cannot read file ${filePath}: ${ex.message}`);
  }
  if (content.length === 0) {
    fail("upload-artifact: file must not be empty");
  }
  const form = new FormData();
  form.append("file", new Blob([content]), filePath.split("/").pop());
  const response = await fetch(
    `${baseUrl()}/api/v1/agent-tools/cli/tasks/${taskId}/artifacts`,
    {
      method: "POST",
      headers: authHeaders(),
      body: form,
    });
  if (!response.ok) {
    fail(`upload-artifact rejected (HTTP ${response.status}): ${await response.text()}`);
  }
  console.log(await response.text());
}

function validate() {
  const kind = process.argv[3];
  const raw = readPayload("validate");
  const node = parseJson("validate", raw);
  if (kind === "decision") {
    validateDecision(node);
  } else if (kind === "plan") {
    validatePlan(node);
  } else if (kind === "verdict") {
    validateVerdict(node);
  } else {
    fail("validate requires one of: decision | plan | verdict");
  }
  console.log(`${kind}: valid`);
}

async function getTask() {
  const taskId = requireFlag("task");
  const response = await fetch(
    `${baseUrl()}/api/v1/agent-tools/cli/tasks/${taskId}`,
    { method: "GET", headers: authHeaders() });
  if (!response.ok) {
    fail(`get-task rejected (HTTP ${response.status}): ${await response.text()}`);
  }
  console.log(await response.text());
}

async function getArtifact() {
  const taskId = requireFlag("task");
  const nameIndex = process.argv.indexOf("--name");
  const outputIndex = process.argv.indexOf("--output");
  const wantedName = nameIndex === -1 ? "" : (process.argv[nameIndex + 1] || "");

  const response = await fetch(
    `${baseUrl()}/api/v1/agent-tools/cli/tasks/${taskId}`,
    { method: "GET", headers: authHeaders() });
  if (!response.ok) {
    fail(`get-artifact rejected (HTTP ${response.status}): ${await response.text()}`);
  }
  const detail = JSON.parse(await response.text());
  const attachments = Array.isArray(detail.attachments) ? detail.attachments : [];
  if (attachments.length === 0) {
    fail("get-artifact: no attachments available for this task");
  }
  const picked = wantedName
      ? attachments.find((a) => a.fileName === wantedName)
      : attachments[0];
  if (!picked) {
    fail(`get-artifact: no attachment named "${wantedName}"`);
  }
  let url = picked.fileDownloadUrl;
  if (url && url.startsWith("/")) {
    url = baseUrl() + url;
  }
  const outputPath = outputIndex === -1 ? picked.fileName : process.argv[outputIndex + 1];
  const fileResponse = await fetch(url);
  if (!fileResponse.ok) {
    fail(`get-artifact download failed (HTTP ${fileResponse.status})`);
  }
  const bytes = new Uint8Array(await fileResponse.arrayBuffer());
  writeFileSync(outputPath, bytes);
  console.log(`get-artifact: ${outputPath} (${bytes.length} bytes)`);
}

async function askHuman() {
  const taskId = requireFlag("task");
  const qIndex = process.argv.indexOf("--question");
  let question;
  if (qIndex !== -1) {
    question = process.argv[qIndex + 1] || "";
  } else {
    question = readFileSync(0, "utf8");
  }
  if (!question || !question.trim()) {
    fail("ask-human requires a non-empty question");
  }
  const response = await fetch(
    `${baseUrl()}/api/v1/agent-tools/cli/tasks/${taskId}/human-request`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify({ question }),
    });
  if (!response.ok) {
    fail(`ask-human rejected (HTTP ${response.status}): ${await response.text()}`);
  }
  console.log(await response.text());
}

async function submitResult() {
  const taskId = requireFlag("task");
  const textIndex = process.argv.indexOf("--text");
  let resultText;
  if (textIndex !== -1) {
    resultText = process.argv[textIndex + 1] || "";
  } else {
    const fileIndex = process.argv.indexOf("--file");
    if (fileIndex !== -1) {
      const path = process.argv[fileIndex + 1];
      if (!path) {
        fail("submit-result --file requires a path");
      }
      try {
        resultText = readFileSync(path, "utf8");
      } catch (ex) {
        fail(`cannot read file ${path}: ${ex.message}`);
      }
    } else {
      resultText = readFileSync(0, "utf8");
    }
  }
  if (!resultText || !resultText.trim()) {
    fail("submit-result requires a non-empty result text");
  }
  const response = await fetch(
    `${baseUrl()}/api/v1/agent-tools/cli/tasks/${taskId}/result`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify({ result_text: resultText }),
    });
  if (!response.ok) {
    fail(`submit-result rejected (HTTP ${response.status}): ${await response.text()}`);
  }
  console.log("submit-result: accepted");
}

async function health() {
  const response = await fetch(`${baseUrl()}/health`);
  if (!response.ok) {
    fail(`health check failed (HTTP ${response.status})`);
  }
  console.log(await response.text());
}

// ── Entry ──────────────────────────────────────────────────────────────

const command = process.argv[2];
if (command === "-h" || command === "--help" || command === "help") {
  printOverview();
  process.exit(0);
}
if (command === "-v" || command === "--version") {
  console.log(VERSION);
  process.exit(0);
}
if (process.argv.includes("-h") || process.argv.includes("--help")) {
  printCommandHelp(command);
  process.exit(0);
}
const routes = {
  "submit-decision": () => submit("decision", "/api/v1/agent-tools/cli/submit-decision"),
  "submit-plan": () => submit("plan", "/api/v1/agent-tools/cli/submit-plan"),
  "submit-verdict": () => submit("verdict", "/api/v1/agent-tools/cli/submit-verdict"),
  "get-task": getTask,
  "get-artifact": getArtifact,
  "submit-result": submitResult,
  "ask-human": askHuman,
  "upload-artifact": uploadArtifact,
  "validate": validate,
  "health": health,
};

if (!routes[command]) {
  console.error(`tc: unknown command "${command || ""}"\n`);
  printOverview();
  process.exit(1);
}

try {
  await routes[command]();
} catch (ex) {
  fail(ex && ex.message ? ex.message : String(ex));
}
