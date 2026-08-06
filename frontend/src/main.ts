import {
  getIdentity,
  setIdentity,
  getProject,
  updateProject,
  listProjects,
  createProject,
  createTask,
  getTask,
  listProjectTasks,
  deleteTask,
  sendMessage,
  respondHumanRequest,
  listAvailableExperts,
  addProjectExpert,
  removeProjectExpert,
  addProjectMember,
  removeProjectMember,
  type Project,
  type Task,
  type HumanRequest,
  type ExpertInfo,
} from "./api";
import { SseStream, type SseEvent } from "./sse";

// ── Application State ───────────────────────────────────────

const state = {
  projectId: localStorage.getItem("projectId") || "",
  taskId: localStorage.getItem("taskId") || "",
  stream: null as SseStream | null,
  projects: [] as Project[],
  tasks: new Map<string, Task[]>(), // projectId → tasks
  pendingHumanRequest: null as HumanRequest | null,
};

function save() {
  localStorage.setItem("projectId", state.projectId);
  localStorage.setItem("taskId", state.taskId);
}

// ── DOM helpers ──────────────────────────────────────────────

const $ = (id: string) => document.getElementById(id)!;
const esc = (v: unknown) =>
  String(v ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");

function time(v?: string | number) {
  if (!v) return "";
  return new Date(v).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

// ── Init ─────────────────────────────────────────────────────

async function init() {
  const app = document.getElementById("app")!;
  app.innerHTML = `
    <div class="layout">
      <aside id="sidebar" class="sidebar">
        <div class="sidebar-header">
          <div class="brand">TeamCoordinator</div>
        </div>
        <div id="sidebar-tree" class="sidebar-tree"></div>
        <div id="sidebar-actions" class="sidebar-actions"></div>
        <div id="sidebar-identity" class="sidebar-identity"></div>
      </aside>
      <main id="main-content" class="main-content"></main>
    </div>
    <div id="dialog-overlay" class="overlay" style="display:none"></div>
  `;

  renderSidebar();
  renderIdentity();

  // Load project list from backend
  try {
    state.projects = await listProjects();
    // Load tasks for current project
    if (state.projectId) {
      try {
        const tasks = await listProjectTasks(state.projectId);
        state.tasks.set(state.projectId, tasks);
      } catch { /* quiet */ }
    }
  } catch {
    // Backend unreachable — projects list stays empty
  }
  refreshSidebar();

  // Default view
  if (state.projectId && state.taskId) {
    selectTask(state.projectId, state.taskId);
  } else if (state.projectId) {
    showProjectDetail(state.projectId);
  } else {
    showWelcome();
  }
}

// ── Sidebar ──────────────────────────────────────────────────

function renderSidebar() {
  renderTree();
  renderSidebarActions();
}

function renderTree() {
  const el = document.getElementById("sidebar-tree")!;
  const items: string[] = [];

  for (const proj of state.projects) {
    const isActive = state.projectId === proj.id;
    const tasks = state.tasks.get(proj.id) || [];
    const expanded = state.projectId === proj.id;
    items.push(`
      <div class="tree-folder ${expanded ? "active" : ""}">
        <div class="tree-row project-row"
             data-action="select-project"
             data-project="${esc(proj.id)}">
          <span class="tree-arrow" data-action="toggle-expand" data-project="${esc(proj.id)}">${expanded ? "▼" : "▶"}</span>
          <span class="tree-icon">📁</span>
          <span class="tree-label">${esc(proj.name)}</span>
        </div>
        ${expanded ? renderTaskTree(proj.id, tasks) : ""}
      </div>
    `);
  }

  if (items.length === 0) {
    items.push(
      `<div class="tree-empty">暂无项目<br><small>点击下方按钮创建</small></div>`
    );
  }

  el.innerHTML = items.join("");
  bindTreeEvents();
}

function renderTaskTree(projectId: string, tasks: Task[]) {
  if (tasks.length === 0) return '<div class="tree-empty-sub">暂无任务</div>';
  return tasks
    .map((t) => {
      const isActive = state.taskId === t.taskId;
      return `
      <div class="tree-row task-row ${isActive ? "active" : ""}"
           data-action="select-task"
           data-project="${esc(projectId)}"
           data-task="${esc(t.taskId)}">
        <span class="tree-icon">💬</span>
        <span class="tree-label">${esc(t.title)}</span>
        ${isActive ? '<span class="tree-badge">●</span>' : ""}
        <button class="tree-del" data-action="delete-task" data-project="${esc(projectId)}" data-task="${esc(t.taskId)}" title="删除">×</button>
      </div>`;
    })
    .join("");
}

function renderSidebarActions() {
  const el = document.getElementById("sidebar-actions")!;
  el.innerHTML = `
    <button id="btn-new-project" class="sidebar-btn">+ 新建项目</button>
    <button id="btn-add-project" class="sidebar-btn">🔗 加入已有项目</button>
    <button id="btn-new-task" class="sidebar-btn" ${
      state.projectId ? "" : "disabled"
    }>+ 新建任务</button>
  `;
  document.getElementById("btn-new-project")!.onclick = showCreateProjectDialog;
  document.getElementById("btn-add-project")!.onclick = showAddProjectDialog;
  document.getElementById("btn-new-task")!.onclick = () => {
    if (state.projectId) showCreateTaskDialog();
  };
}

function renderIdentity() {
  const { tenant, user } = getIdentity();
  const el = document.getElementById("sidebar-identity")!;
  el.innerHTML = `
    <div class="identity-row">
      <span class="identity-label">${esc(tenant)} / ${esc(user)}</span>
      <button class="identity-btn" id="identity-edit-btn">⚙</button>
    </div>
  `;
  document.getElementById("identity-edit-btn")!.onclick = showIdentityDialog;
}

function bindTreeEvents() {
  document.querySelectorAll(".tree-row").forEach((row) => {
    row.addEventListener("click", async (e) => {
      const target = e.target as HTMLElement;
      const action = target.dataset.action || (row as HTMLElement).dataset.action;
      const projectId = (row as HTMLElement).dataset.project!;

      if (action === "delete-task") {
        e.stopPropagation();
        const taskId = target.dataset.task!;
        if (confirm("确定删除这个任务吗？")) {
          deleteTask(projectId, taskId).then(async () => {
            if (state.taskId === taskId) {
              state.taskId = "";
              save();
              showWelcome();
            }
            try {
              const tasks = await listProjectTasks(projectId);
              state.tasks.set(projectId, tasks);
            } catch { /* quiet */ }
            refreshSidebar();
          }).catch(err => alert(String(err)));
        }
        return;
      }

      if (action === "toggle-expand") {
        e.stopPropagation();
        if (state.projectId === projectId) {
          // Collapse: clear project selection
          state.projectId = "";
          state.taskId = "";
          save();
          refreshSidebar();
          showWelcome();
        } else {
          // Expand: select project without navigating to detail
          state.projectId = projectId;
          save();
          try {
            const tasks = await listProjectTasks(projectId);
            state.tasks.set(projectId, tasks);
          } catch { /* quiet */ }
          refreshSidebar();
        }
        return;
      }
      if (action === "select-project") {
        showProjectDetail(projectId);
      } else if (action === "select-task") {
        const taskId = (row as HTMLElement).dataset.task!;
        selectTask(projectId, taskId);
      }
    });
  });
}

function refreshSidebar() {
  renderTree();
  renderSidebarActions();
}


// ── Identity Dialog ──────────────────────────────────────────

function showIdentityDialog() {
  const { tenant, user } = getIdentity();
  $("dialog-overlay").innerHTML = `
    <div class="dialog wide">
      <h3>身份设置</h3>
      <label>Tenant ID <input id="dlg-tenant" value="${esc(tenant)}"></label>
      <label>User ID <input id="dlg-user" value="${esc(user)}"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-identity-save">保存</button>
        <button id="dlg-identity-cancel">取消</button>
      </div>
    </div>
  `;
  $("dialog-overlay").style.display = "flex";
  $("dlg-identity-cancel").onclick = () =>
    ($("dialog-overlay").style.display = "none");
  $("dlg-identity-save").onclick = () => {
    setIdentity(
      ($("dlg-tenant") as HTMLInputElement).value.trim(),
      ($("dlg-user") as HTMLInputElement).value.trim()
    );
    $("dialog-overlay").style.display = "none";
    renderIdentity();
  };
}

// ── Create Project Dialog ────────────────────────────────────

function showCreateProjectDialog() {
  $("dialog-overlay").innerHTML = `
    <div class="dialog wide">
      <h3>新建项目</h3>
      <label>名称 <input id="dlg-name" autofocus></label>
      <label>描述 <input id="dlg-desc"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `;
  $("dialog-overlay").style.display = "flex";
  $("dlg-cancel").onclick = () =>
    ($("dialog-overlay").style.display = "none");
  $("dlg-create").onclick = async () => {
    const name = ($("dlg-name") as HTMLInputElement).value.trim();
    if (!name) return;
    try {
      const proj = await createProject(
        name,
        ($("dlg-desc") as HTMLInputElement).value.trim()
      );
      state.projects.unshift(proj);
      state.projectId = proj.id;
      state.taskId = "";
      save();
      $("dialog-overlay").style.display = "none";
      refreshSidebar();
      showProjectDetail(proj.id);
    } catch (err) {
      alert(String(err));
    }
  };
}

function showAddProjectDialog() {
  $("dialog-overlay").innerHTML = `
    <div class="dialog wide">
      <h3>加入已有项目</h3>
      <label>项目 ID <input id="dlg-project-id" autofocus placeholder="粘贴项目 UUID"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-add">加入</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `;
  $("dialog-overlay").style.display = "flex";
  $("dlg-cancel").onclick = () =>
    ($("dialog-overlay").style.display = "none");
  $("dlg-add").onclick = async () => {
    const id = ($("dlg-project-id") as HTMLInputElement).value.trim();
    if (!id) return;
    try {
      const proj = await getProject(id);
      if (!state.projects.find((p) => p.id === id)) {
        state.projects.unshift(proj);
      }
      state.projectId = id;
      state.taskId = "";
      save();
      $("dialog-overlay").style.display = "none";
      refreshSidebar();
      showProjectDetail(id);
    } catch (err) {
      alert(String(err));
    }
  };
}

// ── Create Task Dialog ───────────────────────────────────────

function showCreateTaskDialog() {
  $("dialog-overlay").innerHTML = `
    <div class="dialog wide">
      <h3>新建任务</h3>
      <label>标题 <input id="dlg-title" value="新对话" autofocus></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `;
  $("dialog-overlay").style.display = "flex";
  $("dlg-cancel").onclick = () =>
    ($("dialog-overlay").style.display = "none");
  $("dlg-create").onclick = async () => {
    const title =
      ($("dlg-title") as HTMLInputElement).value.trim() || "新对话";
    try {
      const task = await createTask(state.projectId, title);
      state.taskId = task.taskId;
      save();
      $("dialog-overlay").style.display = "none";
      // Reload tasks from API
      try {
        const tasks = await listProjectTasks(state.projectId);
        state.tasks.set(state.projectId, tasks);
      } catch { /* quiet */ }
      refreshSidebar();
      showChat();
    } catch (err) {
      alert(String(err));
    }
  };
}

// ── Main Views ───────────────────────────────────────────────

function showWelcome() {
  $("main-content").innerHTML = `
    <div class="panel welcome-panel">
      <h2>TeamCoordinator</h2>
      <p>AI Agent 编排服务测试前端</p>
      <p class="hint">从左侧创建或选择一个项目开始</p>
    </div>
  `;
}

let currentProj: Project | null = null;

async function showProjectDetail(projectId: string) {
  state.projectId = projectId;
  save();
  refreshSidebar();

  try {
    currentProj = await getProject(projectId);
    const idx = state.projects.findIndex((p) => p.id === projectId);
    if (idx >= 0) state.projects[idx] = currentProj;
    else state.projects.push(currentProj);
    // Load tasks for sidebar
    const tasks = await listProjectTasks(projectId);
    state.tasks.set(projectId, tasks);
    refreshSidebar();
    renderProjectView("overview");
  } catch (err) {
    $("main-content").innerHTML = `
      <div class="panel error">
        <p>项目加载失败: ${esc(err)}</p>
        <button id="btn-remove-project">从列表中移除</button>
      </div>
    `;
    document.getElementById("btn-remove-project")!.onclick = () => {
      state.projects = state.projects.filter((p) => p.id !== projectId);
      state.projectId = "";
      state.taskId = "";
      save();
      refreshSidebar();
      showWelcome();
    };
  }
}

async function renderProjectView(tab: string) {
  const proj = currentProj;
  if (!proj) return;

  $("main-content").innerHTML = `
    <div class="panel">
      <div class="tabs">
        <button class="tab ${tab === "overview" ? "active" : ""}" id="tab-overview">概述</button>
        <button class="tab ${tab === "settings" ? "active" : ""}" id="tab-settings">配置</button>
      </div>
      <div id="tab-content"></div>
    </div>
  `;

  document.getElementById("tab-overview")!.onclick = () => renderProjectView("overview");
  document.getElementById("tab-settings")!.onclick = () => renderProjectView("settings");

  if (tab === "overview") renderOverview(proj);
  else await renderSettings(proj);
}

function renderOverview(proj: Project) {
  const el = document.getElementById("tab-content")!;
  el.innerHTML = `
    <h2>${esc(proj.name)} <span class="badge">${esc(proj.status)}</span></h2>
    <p>${esc(proj.description || "无描述")}</p>
    <h3>成员 (${proj.members.length})</h3>
    <ul class="detail-list">
      ${proj.members.map((m) => `<li>${esc(m.userId)} <span class="role-tag">${esc(m.role)}</span></li>`).join("")}
    </ul>
    <h3>专家 (${proj.experts.length})</h3>
    <ul class="detail-list">
      ${proj.experts.map((e) => `<li>${esc(e.expertId)} ${e.enabled ? "✅" : "❌"}</li>`).join("")}
    </ul>
    <div class="actions">
      <button class="primary" id="btn-goto-chat">进入对话</button>
    </div>
  `;
  document.getElementById("btn-goto-chat")!.onclick = () => {
    if (state.taskId) selectTask(proj.id, state.taskId);
    else showCreateTaskDialog();
  };
}

async function renderSettings(proj: Project) {
  let availableExperts: ExpertInfo[] = [];
  try {
    availableExperts = await listAvailableExperts();
  } catch { /* use empty list */ }
  const el = document.getElementById("tab-content")!;
  const enabledExpertIds = new Set(proj.experts.filter(e => e.enabled).map(e => e.expertId));

  el.innerHTML = `
    <h2>项目配置</h2>

    <h3>基本信息</h3>
    <div class="form-row">
      <label>名称 <input id="cfg-name" value="${esc(proj.name)}"></label>
      <label>描述 <input id="cfg-desc" value="${esc(proj.description || "")}"></label>
      <button class="primary" id="btn-save-info">保存</button>
    </div>

    <h3>已配置的专家</h3>
    ${proj.experts.length === 0 ? '<p class="hint">暂无专家</p>' : ''}
    <div class="expert-grid">
      ${proj.experts.map(added => {
        const info = availableExperts.find(e => e.id === added.expertId);
        return `
        <div class="expert-card added">
          <div class="expert-name">${esc(info?.name || added.expertId)}</div>
          <div class="expert-id">${esc(added.expertId)}</div>
          <div class="expert-desc">${esc(info?.description || "")}</div>
          <div class="expert-actions">
            <label><input type="checkbox" class="toggle-expert" data-expert="${esc(added.expertId)}" ${added.enabled ? "checked" : ""}> 启用</label>
            <button class="small danger btn-remove-expert" data-expert="${esc(added.expertId)}">移除</button>
          </div>
        </div>`;
      }).join("")}
    </div>
    <button class="primary" id="btn-add-expert">+ 添加专家</button>

    <h3>成员</h3>
    <div id="member-list">
      ${proj.members.map(m => `
        <div class="member-row">
          <span>${esc(m.userId)}</span>
          ${m.role !== "OWNER"
            ? `<select class="role-select" data-user="${esc(m.userId)}">
                <option value="ADMIN" ${m.role === "ADMIN" ? "selected" : ""}>ADMIN</option>
                <option value="MEMBER" ${m.role === "MEMBER" ? "selected" : ""}>MEMBER</option>
                <option value="VIEWER" ${m.role === "VIEWER" ? "selected" : ""}>VIEWER</option>
              </select>
              <button class="small danger btn-remove-member" data-user="${esc(m.userId)}">移除</button>`
            : `<span class="role-tag">OWNER</span>`
          }
        </div>
      `).join("")}
    </div>
    <div class="form-row" style="margin-top:8px">
      <input id="new-member-id" placeholder="用户 ID">
      <select id="new-member-role">
        <option value="MEMBER">MEMBER</option>
        <option value="ADMIN">ADMIN</option>
        <option value="VIEWER">VIEWER</option>
      </select>
      <button class="primary" id="btn-add-member">添加成员</button>
    </div>
  `;

  // Save project info
  document.getElementById("btn-save-info")!.onclick = async () => {
    const name = ($("cfg-name") as HTMLInputElement).value.trim();
    const desc = ($("cfg-desc") as HTMLInputElement).value.trim();
    if (!name) return;
    try {
      await updateProject(proj.id, { name, description: desc });
      currentProj = await getProject(proj.id);
      renderProjectView("settings");
    } catch (err) { alert(String(err)); }
  };

  // Add expert dialog
  document.getElementById("btn-add-expert")!.onclick = () => {
    const alreadyAdded = new Set(proj.experts.map(e => e.expertId));
    const available = availableExperts.filter(e => !alreadyAdded.has(e.id));
    $("dialog-overlay").innerHTML = `
      <div class="dialog wide">
        <h3>添加专家</h3>
        ${available.length === 0 ? '<p>所有专家已添加</p>' : ''}
        <div style="max-height:300px;overflow-y:auto">
        ${available.map(e => `
          <div class="expert-card" style="cursor:pointer" data-add-expert="${esc(e.id)}">
            <div class="expert-name">${esc(e.name)}</div>
            <div class="expert-id">${esc(e.id)}</div>
            <div class="expert-desc">${esc(e.description)}</div>
          </div>
        `).join("")}
        </div>
        <div class="dialog-actions">
          <button id="dlg-cancel">取消</button>
        </div>
      </div>`;
    $("dialog-overlay").style.display = "flex";
    $("dlg-cancel").onclick = () => ($("dialog-overlay").style.display = "none");
    document.querySelectorAll("[data-add-expert]").forEach(card => {
      card.addEventListener("click", async () => {
        const expertId = (card as HTMLElement).dataset.addExpert!;
        try {
          await addProjectExpert(proj.id, expertId, true);
          currentProj = await getProject(proj.id);
          $("dialog-overlay").style.display = "none";
          renderProjectView("settings");
        } catch (err) { alert(String(err)); }
      });
    });
  };

  // Toggle expert
  el.querySelectorAll(".toggle-expert").forEach(cb => {
    cb.addEventListener("change", async () => {
      const expertId = (cb as HTMLElement).dataset.expert!;
      const enabled = (cb as HTMLInputElement).checked;
      try {
        await addProjectExpert(proj.id, expertId, enabled);
        currentProj = await getProject(proj.id);
      } catch (err) { alert(String(err)); }
    });
  });

  // Remove expert
  el.querySelectorAll(".btn-remove-expert").forEach(btn => {
    btn.addEventListener("click", async () => {
      const expertId = (btn as HTMLElement).dataset.expert!;
      try {
        await removeProjectExpert(proj.id, expertId);
        currentProj = await getProject(proj.id);
        renderProjectView("settings");
      } catch (err) { alert(String(err)); }
    });
  });

  // Add member
  document.getElementById("btn-add-member")!.onclick = async () => {
    const userId = ($("new-member-id") as HTMLInputElement).value.trim();
    const role = ($("new-member-role") as HTMLSelectElement).value;
    if (!userId) return;
    try {
      await addProjectMember(proj.id, userId, role);
      currentProj = await getProject(proj.id);
      renderProjectView("settings");
    } catch (err) { alert(String(err)); }
  };

  // Change member role
  el.querySelectorAll(".role-select").forEach(sel => {
    sel.addEventListener("change", async () => {
      const userId = (sel as HTMLElement).dataset.user!;
      const role = (sel as HTMLSelectElement).value;
      try {
        await addProjectMember(proj.id, userId, role);
        currentProj = await getProject(proj.id);
        renderProjectView("settings");
      } catch (err) { alert(String(err)); }
    });
  });

  // Remove member
  el.querySelectorAll(".btn-remove-member").forEach(btn => {
    btn.addEventListener("click", async () => {
      const userId = (btn as HTMLElement).dataset.user!;
      try {
        await removeProjectMember(proj.id, userId);
        currentProj = await getProject(proj.id);
        renderProjectView("settings");
      } catch (err) { alert(String(err)); }
    });
  });
}

function selectTask(projectId: string, taskId: string) {
  state.stream?.disconnect();
  state.projectId = projectId;
  state.taskId = taskId;
  state.pendingHumanRequest = null;
  save();
  refreshSidebar();
  showChat();
}

// ── Chat ─────────────────────────────────────────────────────

async function showChat() {
  if (!state.projectId || !state.taskId) {
    showWelcome();
    return;
  }

  // Reset reply bubble tracking for the new chat DOM
  replyBubble = null;

  $("main-content").innerHTML = `
    <div class="chat-container">
      <div class="chat-header">
        <span id="chat-title">加载中...</span>
        <span id="connection-status" class="dot offline">未连接</span>
      </div>
      <div id="chat-timeline" class="chat-timeline">
        <div class="welcome"><p>加载历史记录...</p></div>
      </div>
      <div id="hitl-panel" class="hitl-panel hidden"></div>
      <form id="chat-form" class="chat-form">
        <textarea id="chat-input" rows="1" placeholder="输入消息..."></textarea>
        <button type="submit" class="primary">发送</button>
      </form>
    </div>
  `;

  // Auto-resize textarea
  const input = $("chat-input") as HTMLTextAreaElement;
  input.addEventListener("input", () => {
    input.style.height = "auto";
    input.style.height = Math.min(input.scrollHeight, 120) + "px";
  });
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      ($("chat-form") as HTMLFormElement).requestSubmit();
    }
  });

  // Load task title for sidebar cache + chat header
  try {
    const task = await getTask(state.projectId, state.taskId);
    const tasks = state.tasks.get(state.projectId) || [];
    if (!tasks.find((t) => t.taskId === task.taskId)) {
      tasks.push(task);
      state.tasks.set(state.projectId, tasks);
      refreshSidebar();
    }
    const titleEl = document.getElementById("chat-title");
    if (titleEl) titleEl.textContent = task.title;
  } catch (err: any) {
    const msg = err?.message || String(err);
    if (msg.includes("VIEWER") || msg.includes("FORBIDDEN") || msg.includes("403")) {
      state.taskId = "";
      save();
      $("main-content").innerHTML = `
        <div class="panel" style="margin-top:60px;text-align:center">
          <h3>权限不足</h3>
          <p>你当前是 VIEWER 角色，只能查看项目，不能进入对话。</p>
          <button id="btn-back-project" class="primary" style="margin-top:12px">返回项目</button>
        </div>
      `;
      document.getElementById("btn-back-project")!.onclick = () => showProjectDetail(state.projectId);
      refreshSidebar();
      return;
    }
    const titleEl = document.getElementById("chat-title");
    if (titleEl) titleEl.textContent = "对话";
  }

  // Start SSE — backend replays all history via Last-Event-ID: 0
  startStream();

  // Chat form submit
  $("chat-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    input.value = "";
    input.style.height = "auto";
    try {
      await sendMessage(state.projectId, state.taskId, text);
    } catch (err) {
      addMessageBubble("error", String(err), "system");
    }
  });
}

function startStream() {
  state.stream?.disconnect();
  state.stream = new SseStream(state.projectId, state.taskId);
  state.stream.setLastSequence(0);

  // Clear timeline and reset reply tracking
  replyBubble = null;
  const tl = document.getElementById("chat-timeline");
  if (tl) tl.innerHTML = "";

  state.stream.onEvent((event) => {
    appendSseEvent(event);
  });

  state.stream.connect();
  const dot = document.getElementById("connection-status");
  if (dot) {
    dot.className = "dot online";
    dot.textContent = "已连接";
  }
}

function addMessageBubble(cls: string, text: string, sender: string) {
  const tl = document.getElementById("chat-timeline");
  if (!tl) return;
  tl.insertAdjacentHTML(
    "beforeend",
    `<div class="bubble ${cls}">
      <div class="meta">${esc(sender)} · ${time(Date.now())}</div>
      <div class="text">${esc(text)}</div>
    </div>`
  );
  tl.scrollTop = tl.scrollHeight;
}

// Track the current reply bubble
let replyBubble: HTMLElement | null = null;
let replyAgent = "";

function appendSseEvent(event: Partial<SseEvent>) {
  const tl = document.getElementById("chat-timeline");
  if (!tl) return;
  const type = event.type || "";

  if (type === "confirm" || type === "coordinatorConfirm") {
    state.pendingHumanRequest = {
      id: (event.questionId as string) || "",
      question: (event.content as string) || "Agent 需要你的确认",
      type: "CLARIFICATION",
      status: "PENDING",
    };
    showHitlPanel(event as SseEvent);
  }

  // userMessage: finalize current reply, create user bubble
  if (type === "userMessage") {
    replyBubble = null;
    replyAgent = "";
    const sender = event.agentId || "user";
    const isMe = sender === getIdentity().user;
    tl.insertAdjacentHTML(
      "beforeend",
      `<div class="bubble ${isMe ? "user" : "other"}">
        <div class="meta">${esc(sender)} · ${time(event.timestamp)}</div>
        <div class="text">${esc(event.content || "")}</div>
      </div>`
    );
    tl.scrollTop = tl.scrollHeight;
    return;
  }

  // Build display text for this event
  let text = "";
  if (type === "thinkingDelta" || type === "thinking") {
    text = event.text || "";
  } else if (type === "textDelta") {
    text = event.text || "";
  } else if (type === "chat" || type === "coordinatorChat") {
    text = event.content || "";
  } else if (type === "end") {
    // Skip Coordinator end events that carry JSON decisions
    if (!(event.content || "").startsWith("{")) {
      text = event.content || "";
    }
  } else if (type === "coordinatorPhase" || type === "liveStatus"
      || type === "coordinatorPlanUpdate" || type === "coordinatorNewPlanStep"
      || type === "coordinatorError" || type === "planUpdate"
      || type === "newPlanStep" || type === "error" || type === "taskInQueue") {
    const label = eventLabel({ type, ...event } as SseEvent);
    text = event.content || event.text || label || "";
    if (!text && (type === "coordinatorPlanUpdate" || type === "planUpdate")) {
      const tasks = event.tasks as Array<{ title: string }> | undefined;
      if (tasks?.length) text = "计划: " + tasks.map(t => t.title).join(", ");
    }
  }

  if (!text) return;

  // Track agent for label
  if (event.agentId && event.agentId !== replyAgent) {
    replyAgent = event.agentId;
  }

  // Create reply bubble on first non-user event
  if (!replyBubble) {
    replyBubble = document.createElement("div");
    replyBubble.className = "bubble system";
    replyBubble.innerHTML = `
      <div class="reply-agent">${esc(replyAgent)}</div>
      <div class="reply-output"></div>
    `;
    tl.appendChild(replyBubble);
  }

  // Append text to output — streaming effect
  const outputEl = replyBubble.querySelector(".reply-output") as HTMLElement;

  // Streaming text: append in place (no newline — streaming effect)
  if (type === "thinkingDelta" || type === "thinking") {
    outputEl.textContent += text;
  } else if (type === "textDelta") {
    outputEl.textContent += text;
  } else if (type === "chat" || type === "coordinatorChat") {
    // Final answer: add with separator, don't overwrite accumulated progress
    if (outputEl.textContent) outputEl.textContent += "\n\n";
    outputEl.textContent += text;
  } else if (type === "end" && !(event.content || "").startsWith("{")) {
    // Expert end event with content
    if (outputEl.textContent) outputEl.textContent += "\n\n";
    outputEl.textContent += text;
  } else {
    // Status/phase events: timestamped line
    if (outputEl.textContent) outputEl.textContent += "\n";
    outputEl.textContent += "[" + time(event.timestamp) + "] " + text;
  }

  tl.scrollTop = tl.scrollHeight;
}

function eventLabel(e: SseEvent | Record<string, unknown>): string | null {
  const ev = e as SseEvent;
  const type = ev.type || "";
  const status = ev.status || "";
  const text = ev.content || ev.text || "";
  const labels: Record<string, string> = {
    userMessage: "用户消息", coordinatorPhase: "阶段",
    coordinatorChat: "回复", coordinatorConfirm: "需要确认",
    coordinatorError: "错误", coordinatorPlanUpdate: "计划更新",
    coordinatorNewPlanStep: "任务开始", coordinatorRunCancelled: "已取消",
    liveStatus: "", thinkingStart: "开始思考", thinkingDelta: "思考中",
    thinking: "思考", thinkingEnd: "思考结束", textDelta: "输出中",
    streamStart: "开始输出", streamEnd: "输出结束", chat: "回复",
    end: "完成", error: "错误", confirm: "需要确认",
    planUpdate: "计划更新", newPlanStep: "任务开始",
    taskInQueue: "排队中", toolUsed: "工具调用", toolResult: "工具结果",
    weblink: "打开链接", file: "文件", directory: "目录",
    sidebarDisplay: "工作区", clearBoundary: "上下文清理",
    compactBoundary: "上下文压缩", reconnect: "重连",
  };
  if (type === "coordinatorPhase") {
    const phaseLabels: Record<string, string> = {
      analyzing: "正在理解需求", planning: "正在制定计划",
      dispatching: "正在分配专家", answering: "正在整理回复",
      waiting_human: "需要确认", completed: "已完成", failed: "执行失败",
    };
    return phaseLabels[status] || text || "阶段转换";
  }
  if (type === "liveStatus") return text || "状态更新";
  return labels[type] || text || null;
}

function showHitlPanel(event: SseEvent) {
  const panel = document.getElementById("hitl-panel");
  if (!panel) return;

  const question = (event.content as string) || "Agent 需要你的确认";
  const questions = event.questions as
    | Array<{
        question: string;
        header: string;
        options?: Array<{ label: string; description: string }>;
        multiSelect: boolean;
      }>
    | undefined;

  panel.className = "hitl-panel";
  if (questions?.length) {
    panel.innerHTML =
      questions
        .map(
          (q) => `
      <div class="hitl-question">
        <strong>${esc(q.header || q.question)}</strong>
        <p>${esc(q.question)}</p>
        ${
          q.options?.length
            ? `<div>${q.options
                .map(
                  (o) =>
                    `<label class="hitl-option"><input type="${
                      q.multiSelect ? "checkbox" : "radio"
                    }" name="hitl-${esc(q.header)}" value="${esc(o.label)}"> ${esc(o.label)} — ${esc(o.description)}</label>`
                )
                .join("")}</div>`
            : `<input class="hitl-input" data-question="${esc(q.question)}" placeholder="输入你的回答...">`
        }
      </div>`
        )
        .join("") +
      `<button class="primary" id="hitl-submit">提交回答</button>`;
    $("hitl-submit").onclick = async () => {
      const response: Record<string, string> = {};
      panel.querySelectorAll(".hitl-input").forEach((el) => {
        const input = el as HTMLInputElement;
        response[input.dataset.question || "answer"] = input.value.trim();
      });
      panel
        .querySelectorAll<HTMLInputElement>("input:checked")
        .forEach((el) => {
          response[el.name.replace("hitl-", "")] = el.value;
        });
      await submitHitl(response);
    };
  } else {
    panel.innerHTML = `
      <strong>${esc(question)}</strong>
      <input id="hitl-answer" placeholder="输入回答...">
      <button class="primary" id="hitl-submit">提交</button>
    `;
    $("hitl-submit").onclick = async () => {
      const answer = ($("hitl-answer") as HTMLInputElement).value.trim();
      if (!answer) return;
      await submitHitl({ answer });
    };
  }
}

async function submitHitl(response: Record<string, string>) {
  const hr = state.pendingHumanRequest;
  if (!hr) return;
  try {
    await respondHumanRequest(state.projectId, hr.id, "ANSWER", response);
    state.pendingHumanRequest = null;
    const panel = document.getElementById("hitl-panel");
    if (panel) panel.className = "hitl-panel hidden";
  } catch (err) {
    alert(String(err));
  }
}

// ── Start ────────────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", init);
