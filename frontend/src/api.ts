export interface Project {
  id: string;
  name: string;
  description?: string;
  coordinatorAgentId?: string;
  status: string;
  members: ProjectMember[];
  experts: ProjectExpert[];
  skills: Skill[];
}

export interface ProjectMember {
  userId: string;
  role: "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";
}

export interface ProjectExpert {
  expertId: string;
  enabled: boolean;
}

export interface Skill {
  id: string;
  name: string;
  description?: string;
  prompt?: string;
  enabled: boolean;
}

export interface Task {
  taskId: string;
  projectId: string;
  sessionId: string;
  title: string;
  status: string;
  createdAt: string;
}

export interface Workspace {
  project: Project;
  task: Task;
  messages: Message[];
  events: ProjectEvent[];
  tasks: PlanTask[];
  artifacts: Artifact[];
  humanRequests: HumanRequest[];
}

export interface Message {
  messageId: string;
  userId: string;
  messageText: string;
  attachmentRefs: string[];
  createdAt: string;
}

export interface ProjectEvent {
  sequence: number;
  eventType: string;
  payload: string;
  createdAt: string;
}

export interface PlanTask {
  taskId: string;
  taskKey: string;
  expertId: string;
  status: string;
  objective: string;
  expectedOutput: string;
  dependencies: string[];
}

export interface Artifact {
  id: string;
  fileName: string;
  mediaType: string;
  version: number;
  sizeBytes?: number;
  status: string;
  downloadUrl?: string;
}

export interface HumanRequest {
  id: string;
  taskId?: string;
  type: "CLARIFICATION" | "APPROVAL";
  question: string;
  status: "PENDING" | "RESOLVED" | "EXPIRED";
  decision?: string;
  expiresAt?: string;
}

let tenant = localStorage.getItem("tenant") || "demo-tenant";
let user = localStorage.getItem("user") || "demo-owner";

export function setIdentity(t: string, u: string) {
  tenant = t;
  user = u;
  localStorage.setItem("tenant", t);
  localStorage.setItem("user", u);
}

export function getIdentity() {
  return { tenant, user };
}

function headers(): Record<string, string> {
  return {
    "X-Tenant-Id": tenant,
    "X-User-Id": user,
    "Content-Type": "application/json",
  };
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const res = await fetch(path, {
    ...options,
    headers: { ...headers(), ...(options.headers || {}) },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(body.message || `HTTP ${res.status}`);
  }
  if (res.status === 204) return null as T;
  return res.json();
}

// ── Projects ────────────────────────────────────────────────

export function listProjects(): Promise<Project[]> {
  return request<Project[]>("/api/v1/projects");
}

export function getProject(id: string): Promise<Project> {
  return request<Project>(`/api/v1/projects/${id}`);
}

export function createProject(name: string, description?: string): Promise<Project> {
  return request<Project>("/api/v1/projects", {
    method: "POST",
    body: JSON.stringify({ name, description: description || "" }),
  });
}

export function updateProject(
  id: string,
  data: { name?: string; description?: string; coordinatorAgentId?: string; status?: string }
): Promise<Project> {
  return request<Project>(`/api/v1/projects/${id}`, {
    method: "PATCH",
    body: JSON.stringify(data),
  });
}

// ── Tasks ────────────────────────────────────────────────────

export function createTask(projectId: string, title: string): Promise<Task> {
  return request<Task>(`/api/v1/projects/${projectId}/tasks`, {
    method: "POST",
    body: JSON.stringify({ title }),
  });
}

export function getTask(projectId: string, taskId: string): Promise<Task> {
  return request<Task>(`/api/v1/projects/${projectId}/tasks/${taskId}`);
}

export function deleteTask(projectId: string, taskId: string): Promise<void> {
  return request(`/api/v1/projects/${projectId}/tasks/${taskId}`, {
    method: "DELETE",
  });
}

// ── Messages ─────────────────────────────────────────────────

export function sendMessage(
  projectId: string,
  taskId: string,
  text: string
): Promise<{ messageId: string; status: string }> {
  const clientId = crypto.randomUUID();
  return request(`/api/v1/projects/${projectId}/tasks/${taskId}/messages`, {
    method: "POST",
    body: JSON.stringify({
      client_message_id: clientId,
      text,
      attachment_refs: [],
      idempotency_key: clientId,
    }),
  });
}

// ── Workspace ────────────────────────────────────────────────

export function getWorkspace(
  projectId: string,
  taskId: string
): Promise<Workspace> {
  return request<Workspace>(
    `/api/v1/projects/${projectId}/tasks/${taskId}/workspace`
  );
}

// ── Human Requests ───────────────────────────────────────────

export function respondHumanRequest(
  projectId: string,
  requestId: string,
  decision: string,
  response: Record<string, string>
): Promise<void> {
  return request(
    `/api/v1/projects/${projectId}/human-requests/${requestId}/responses`,
    {
      method: "POST",
      body: JSON.stringify({
        decision,
        response,
        idempotencyKey: crypto.randomUUID(),
      }),
    }
  );
}

// ── Experts ──────────────────────────────────────────────────

export interface ExpertInfo {
  id: string;
  name: string;
  description: string;
  prompt: string;
}

export function listAvailableExperts(): Promise<ExpertInfo[]> {
  return request<ExpertInfo[]>("/api/v1/experts");
}

// ── Tasks ────────────────────────────────────────────────────

export function listProjectTasks(projectId: string): Promise<Task[]> {
  return request<Task[]>(`/api/v1/projects/${projectId}/tasks`);
}

// ── Project Experts ──────────────────────────────────────────

export function addProjectExpert(
  projectId: string,
  expertId: string,
  enabled: boolean
): Promise<Project> {
  return request<Project>(`/api/v1/projects/${projectId}/experts`, {
    method: "POST",
    body: JSON.stringify({ expertId, enabled }),
  });
}

export function removeProjectExpert(
  projectId: string,
  expertId: string
): Promise<void> {
  return request(`/api/v1/projects/${projectId}/experts/${expertId}`, {
    method: "DELETE",
  });
}

// ── Members ──────────────────────────────────────────────────

export function addProjectMember(
  projectId: string,
  userId: string,
  role: string
): Promise<Project> {
  return request<Project>(`/api/v1/projects/${projectId}/members`, {
    method: "POST",
    body: JSON.stringify({ userId, role }),
  });
}

export function removeProjectMember(
  projectId: string,
  userId: string
): Promise<void> {
  return request(`/api/v1/projects/${projectId}/members/${userId}`, {
    method: "DELETE",
  });
}

// ── Skills ────────────────────────────────────────────────────

export function listAvailableSkills(): Promise<Skill[]> {
  return request<Skill[]>("/api/v1/skills");
}

export function listProjectSkills(projectId: string): Promise<Skill[]> {
  return request<Skill[]>(`/api/v1/projects/${projectId}/skills`);
}

export function addProjectSkill(
  projectId: string,
  skillId: string,
  enabled: boolean = true
): Promise<Project> {
  return request<Project>(`/api/v1/projects/${projectId}/skills`, {
    method: "POST",
    body: JSON.stringify({ skillId, enabled }),
  });
}

export function removeProjectSkill(
  projectId: string,
  skillId: string
): Promise<void> {
  return request(`/api/v1/projects/${projectId}/skills/${skillId}`, {
    method: "DELETE",
  });
}

// ── Artifacts ────────────────────────────────────────────────

export function getArtifact(
  projectId: string,
  artifactId: string
): Promise<{ downloadUrl: string }> {
  return request(`/api/v1/projects/${projectId}/artifacts/${artifactId}`);
}
