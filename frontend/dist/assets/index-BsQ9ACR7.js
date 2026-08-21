var nt=Object.defineProperty;var at=(t,n,e)=>n in t?nt(t,n,{enumerable:!0,configurable:!0,writable:!0,value:e}):t[n]=e;var P=(t,n,e)=>at(t,typeof n!="symbol"?n+"":n,e);(function(){const n=document.createElement("link").relList;if(n&&n.supports&&n.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))a(i);new MutationObserver(i=>{for(const d of i)if(d.type==="childList")for(const c of d.addedNodes)c.tagName==="LINK"&&c.rel==="modulepreload"&&a(c)}).observe(document,{childList:!0,subtree:!0});function e(i){const d={};return i.integrity&&(d.integrity=i.integrity),i.referrerPolicy&&(d.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?d.credentials="include":i.crossOrigin==="anonymous"?d.credentials="omit":d.credentials="same-origin",d}function a(i){if(i.ep)return;i.ep=!0;const d=e(i);fetch(i.href,d)}})();let B=localStorage.getItem("tenant")||"demo-tenant",O=localStorage.getItem("user")||"demo-owner";function it(t,n){B=t,O=n,localStorage.setItem("tenant",t),localStorage.setItem("user",n)}function j(){return{tenant:B,user:O}}function st(){return{"X-Tenant-Id":B,"X-User-Id":O,"Content-Type":"application/json"}}async function m(t,n={}){const e=await fetch(t,{...n,headers:{...st(),...n.headers||{}}});if(!e.ok){const a=await e.json().catch(()=>({message:e.statusText}));throw new Error(a.message||`HTTP ${e.status}`)}return e.status===204?null:e.json()}function Q(){return m("/api/v1/projects")}function f(t){return m(`/api/v1/projects/${t}`)}function ot(t,n){return m("/api/v1/projects",{method:"POST",body:JSON.stringify({name:t,description:n||""})})}function rt(t,n){return m(`/api/v1/projects/${t}`,{method:"PATCH",body:JSON.stringify(n)})}function dt(t,n){return m(`/api/v1/projects/${t}/tasks`,{method:"POST",body:JSON.stringify({title:n})})}function lt(t,n){return m(`/api/v1/projects/${t}/tasks/${n}`)}function ct(t,n){return m(`/api/v1/projects/${t}/tasks/${n}`,{method:"DELETE"})}function pt(t,n,e){const a=crypto.randomUUID();return m(`/api/v1/projects/${t}/tasks/${n}/messages`,{method:"POST",body:JSON.stringify({client_message_id:a,text:e,attachment_refs:[],idempotency_key:a})})}function ut(t,n,e,a){return m(`/api/v1/projects/${t}/human-requests/${n}/responses`,{method:"POST",body:JSON.stringify({decision:e,response:a,idempotencyKey:crypto.randomUUID()})})}function mt(){return m("/api/v1/experts")}function L(t){return m(`/api/v1/projects/${t}/tasks`)}function J(t,n,e){return m(`/api/v1/projects/${t}/experts`,{method:"POST",body:JSON.stringify({expertId:n,enabled:e})})}function vt(t,n){return m(`/api/v1/projects/${t}/experts/${n}`,{method:"DELETE"})}function _(t,n,e){return m(`/api/v1/projects/${t}/members`,{method:"POST",body:JSON.stringify({userId:n,role:e})})}function yt(t,n){return m(`/api/v1/projects/${t}/members/${n}`,{method:"DELETE"})}function ht(){return m("/api/v1/skills")}function K(t,n,e=!0){return m(`/api/v1/projects/${t}/skills`,{method:"POST",body:JSON.stringify({skillId:n,enabled:e})})}function bt(t,n){return m(`/api/v1/projects/${t}/skills/${n}`,{method:"DELETE"})}function gt(t,n){return m(`/api/v1/projects/${t}/artifacts/by-storage/${n}`)}function q(){return m("/api/v1/tenants")}function ft(t){return m(`/api/v1/tenants/${t}/members`)}function It(t,n,e){return m(`/api/v1/tenants/${t}/members`,{method:"POST",body:JSON.stringify({userId:n,role:e})})}function $t(t,n){return m(`/api/v1/tenants/${t}/members/${n}`,{method:"DELETE"})}function S(){return m("/api/v1/admin/tenants")}function kt(t,n,e){return m("/api/v1/admin/tenants",{method:"POST",body:JSON.stringify({name:t,description:e||"",ownerUserId:n})})}function wt(t){return m(`/api/v1/admin/tenants/${t}/disable`,{method:"POST"})}function Et(t){return m(`/api/v1/admin/tenants/${t}`,{method:"DELETE"})}function St(){return m("/api/v1/admin/prompts")}function xt(t){return m("/api/v1/admin/prompts",{method:"POST",body:JSON.stringify(t)})}function Tt(t){return m(`/api/v1/admin/prompts/${t}/publish`,{method:"POST"})}function At(t){return m(`/api/v1/admin/prompts/${t}`,{method:"DELETE"})}function jt(t,n){return m(`/api/v1/admin/tenants/${t}`,{method:"PATCH",body:JSON.stringify(n)})}class Lt{constructor(n,e){P(this,"abort",null);P(this,"lastSequence",0);P(this,"listeners",[]);this.projectId=n,this.taskId=e}onEvent(n){this.listeners.push(n)}setLastSequence(n){this.lastSequence=n}async connect(){var i;(i=this.abort)==null||i.abort();const n=new AbortController;this.abort=n;const{tenant:e,user:a}=j();for(;;)try{const d=await fetch(`/api/v1/projects/${this.projectId}/tasks/${this.taskId}/events`,{headers:{"X-Tenant-Id":e,"X-User-Id":a,"Last-Event-ID":String(this.lastSequence)},signal:n.signal});if(!d.ok)throw new Error(`SSE connect failed: ${d.status}`);const c=d.body.getReader(),h=new TextDecoder;let y="";for(;;){const{done:l,value:u}=await c.read();if(l)break;y+=h.decode(u,{stream:!0});let v;for(;(v=y.indexOf(`

`))>=0;){const p=y.slice(0,v);y=y.slice(v+2);const w=p.split(`
`).find(E=>E.startsWith("id:")),T=w?Number(w.slice(3).trim()):Number.NaN,W=p.split(`
`).filter(E=>E.startsWith("data:")).map(E=>E.slice(5).trim()).join("");if(W)try{const E=JSON.parse(W);Number.isFinite(T)&&(this.lastSequence=Math.max(this.lastSequence,T));for(const et of this.listeners)try{et(E)}catch{}}catch{}}}await V(1e3)}catch(d){if(d instanceof DOMException&&d.name==="AbortError")return;await V(2e3)}}disconnect(){var n;(n=this.abort)==null||n.abort()}}function V(t){return new Promise(n=>setTimeout(n,t))}const o={projectId:localStorage.getItem("projectId")||"",taskId:localStorage.getItem("taskId")||"",stream:null,projects:[],tasks:new Map,pendingHumanRequest:null,isPlatformAdmin:!1,isTenantAdmin:!1};function $(){localStorage.setItem("projectId",o.projectId),localStorage.setItem("taskId",o.taskId)}const r=t=>document.getElementById(t),s=t=>String(t??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");function C(t){return t?new Date(t).toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"}):""}async function Mt(){const t=document.getElementById("app");t.innerHTML=`
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
  `,Pt(),R();try{if(o.projects=await Q(),o.projectId)try{const n=await L(o.projectId);o.tasks.set(o.projectId,n)}catch{}}catch{}await Y(),g(),o.projectId&&o.taskId?U(o.projectId,o.taskId):o.projectId?M(o.projectId):x()}function Pt(){z(),G()}function z(){const t=document.getElementById("sidebar-tree"),n=[];for(const e of o.projects){o.projectId,e.id;const a=o.tasks.get(e.id)||[],i=o.projectId===e.id;n.push(`
      <div class="tree-folder ${i?"active":""}">
        <div class="tree-row project-row"
             data-action="select-project"
             data-project="${s(e.id)}">
          <span class="tree-arrow" data-action="toggle-expand" data-project="${s(e.id)}">${i?"▼":"▶"}</span>
          <span class="tree-icon">📁</span>
          <span class="tree-label">${s(e.name)}</span>
        </div>
        ${i?Nt(e.id,a):""}
      </div>
    `)}n.length===0&&n.push('<div class="tree-empty">暂无项目<br><small>点击下方按钮创建</small></div>'),t.innerHTML=n.join(""),Dt()}function Nt(t,n){return n.length===0?'<div class="tree-empty-sub">暂无任务</div>':n.map(e=>{const a=o.taskId===e.taskId;return`
      <div class="tree-row task-row ${a?"active":""}"
           data-action="select-task"
           data-project="${s(t)}"
           data-task="${s(e.taskId)}">
        <span class="tree-icon">💬</span>
        <span class="tree-label">${s(e.title)}</span>
        ${a?'<span class="tree-badge">●</span>':""}
        <button class="tree-del" data-action="delete-task" data-project="${s(t)}" data-task="${s(e.taskId)}" title="删除">×</button>
      </div>`}).join("")}function G(){const t=document.getElementById("sidebar-actions");t.innerHTML=`
    <button id="btn-new-project" class="sidebar-btn">+ 新建项目</button>
    <button id="btn-add-project" class="sidebar-btn">🔗 加入已有项目</button>
    <button id="btn-new-task" class="sidebar-btn" ${o.projectId?"":"disabled"}>+ 新建任务</button>
  `,document.getElementById("btn-new-project").onclick=Ct,document.getElementById("btn-add-project").onclick=Bt,document.getElementById("btn-new-task").onclick=()=>{o.projectId&&Z()}}function R(){const{tenant:t,user:n}=j(),e=document.getElementById("sidebar-identity"),a=o.isPlatformAdmin||o.isTenantAdmin?'<button class="identity-btn" id="identity-admin-btn">管理</button>':"";e.innerHTML=`
    <div class="identity-row">
      <span class="identity-label">${s(t)} / ${s(n)}</span>
      ${a}
      <button class="identity-btn" id="identity-edit-btn">⚙</button>
    </div>
  `,document.getElementById("identity-edit-btn").onclick=Ht;const i=document.getElementById("identity-admin-btn");i&&(i.onclick=()=>o.isPlatformAdmin?Xt():Vt())}async function Y(){try{await S(),o.isPlatformAdmin=!0,o.isTenantAdmin=!1}catch{o.isPlatformAdmin=!1;try{const t=await q(),{tenant:n}=j(),e=t.find(a=>a.tenantId===n);o.isTenantAdmin=!!e&&e.role==="TENANT_ADMIN"}catch{o.isTenantAdmin=!1}}R()}function Dt(){document.querySelectorAll(".tree-row").forEach(t=>{t.addEventListener("click",async n=>{const e=n.target,a=e.dataset.action||t.dataset.action,i=t.dataset.project;if(a==="delete-task"){n.stopPropagation();const d=e.dataset.task;confirm("确定删除这个任务吗？")&&ct(i,d).then(async()=>{o.taskId===d&&(o.taskId="",$(),x());try{const c=await L(i);o.tasks.set(i,c)}catch{}g()}).catch(c=>alert(String(c)));return}if(a==="toggle-expand"){if(n.stopPropagation(),o.projectId===i)o.projectId="",o.taskId="",$(),g(),x();else{o.projectId=i,$();try{const d=await L(i);o.tasks.set(i,d)}catch{}g()}return}if(a==="select-project")M(i);else if(a==="select-task"){const d=t.dataset.task;U(i,d)}})})}function g(){z(),G()}function Ht(){const{tenant:t,user:n}=j();r("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>身份设置</h3>
      <label>租户 <select id="dlg-tenant"><option>加载中…</option></select></label>
      <label>User ID <input id="dlg-user" value="${s(n)}"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-identity-save">保存</button>
        <button id="dlg-identity-cancel">取消</button>
      </div>
    </div>
  `,r("dialog-overlay").style.display="flex",r("dlg-identity-cancel").onclick=()=>r("dialog-overlay").style.display="none",q().then(e=>{const a=e.filter(c=>c.status==="ACTIVE"),i=r("dlg-tenant");let d=a.map(c=>`<option value="${s(c.tenantId)}">${s(c.name)} (${s(c.tenantId)})</option>`).join("");a.some(c=>c.tenantId===t)||(d=`<option value="${s(t)}" disabled>当前租户（无权限）</option>`+d),i.innerHTML=d||'<option value="" disabled>无可用租户</option>',i.value=t}).catch(e=>{r("dlg-tenant").innerHTML='<option value="" disabled>租户列表加载失败</option>',alert(String(e))}),r("dlg-identity-save").onclick=async()=>{var i;const e=r("dlg-tenant").value,a=r("dlg-user").value.trim();if(!(!e||!a)){it(e,a),r("dialog-overlay").style.display="none",R(),(i=o.stream)==null||i.disconnect(),o.stream=null,o.projectId="",o.taskId="",$(),o.projects=[],o.tasks.clear();try{o.projects=await Q()}catch(d){alert(String(d))}await Y(),g(),x()}}}function Ct(){r("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建项目</h3>
      <label>名称 <input id="dlg-name" autofocus></label>
      <label>描述 <input id="dlg-desc"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,r("dialog-overlay").style.display="flex",r("dlg-cancel").onclick=()=>r("dialog-overlay").style.display="none",r("dlg-create").onclick=async()=>{const t=r("dlg-name").value.trim();if(t)try{const n=await ot(t,r("dlg-desc").value.trim());o.projects.unshift(n),o.projectId=n.id,o.taskId="",$(),r("dialog-overlay").style.display="none",g(),M(n.id)}catch(n){alert(String(n))}}}function Bt(){r("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>加入已有项目</h3>
      <label>项目 ID <input id="dlg-project-id" autofocus placeholder="粘贴项目 UUID"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-add">加入</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,r("dialog-overlay").style.display="flex",r("dlg-cancel").onclick=()=>r("dialog-overlay").style.display="none",r("dlg-add").onclick=async()=>{const t=r("dlg-project-id").value.trim();if(t)try{const n=await f(t);o.projects.find(e=>e.id===t)||o.projects.unshift(n),o.projectId=t,o.taskId="",$(),r("dialog-overlay").style.display="none",g(),M(t)}catch(n){alert(String(n))}}}function Z(){r("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建任务</h3>
      <label>标题 <input id="dlg-title" value="新对话" autofocus></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,r("dialog-overlay").style.display="flex",r("dlg-cancel").onclick=()=>r("dialog-overlay").style.display="none",r("dlg-create").onclick=async()=>{const t=r("dlg-title").value.trim()||"新对话";try{const n=await dt(o.projectId,t);o.taskId=n.taskId,$(),r("dialog-overlay").style.display="none";try{const e=await L(o.projectId);o.tasks.set(o.projectId,e)}catch{}g(),tt()}catch(n){alert(String(n))}}}function x(){r("main-content").innerHTML=`
    <div class="panel welcome-panel">
      <h2>TeamCoordinator</h2>
      <p>AI Agent 编排服务测试前端</p>
      <p class="hint">从左侧创建或选择一个项目开始</p>
    </div>
  `}let b=null;async function M(t){o.projectId=t,$(),g();try{b=await f(t);const n=o.projects.findIndex(a=>a.id===t);n>=0?o.projects[n]=b:o.projects.push(b);const e=await L(t);o.tasks.set(t,e),g(),I("overview")}catch(n){r("main-content").innerHTML=`
      <div class="panel error">
        <p>项目加载失败: ${s(n)}</p>
        <button id="btn-remove-project">从列表中移除</button>
      </div>
    `,document.getElementById("btn-remove-project").onclick=()=>{o.projects=o.projects.filter(e=>e.id!==t),o.projectId="",o.taskId="",$(),g(),x()}}}async function I(t){const n=b;n&&(r("main-content").innerHTML=`
    <div class="panel">
      <div class="tabs">
        <button class="tab ${t==="overview"?"active":""}" id="tab-overview">概述</button>
        <button class="tab ${t==="settings"?"active":""}" id="tab-settings">配置</button>
      </div>
      <div id="tab-content"></div>
    </div>
  `,document.getElementById("tab-overview").onclick=()=>I("overview"),document.getElementById("tab-settings").onclick=()=>I("settings"),t==="overview"?Ot(n):await qt(n))}function Ot(t){var e;const n=document.getElementById("tab-content");n.innerHTML=`
    <h2>${s(t.name)} <span class="badge">${s(t.status)}</span></h2>
    <p>${s(t.description||"无描述")}</p>
    <h3>成员 (${t.members.length})</h3>
    <ul class="detail-list">
      ${t.members.map(a=>`<li>${s(a.userId)} <span class="role-tag">${s(a.role)}</span></li>`).join("")}
    </ul>
    <h3>专家 (${t.experts.length})</h3>
    <ul class="detail-list">
      ${t.experts.map(a=>`<li>${s(a.expertId)} ${a.enabled?"✅":"❌"}</li>`).join("")}
    </ul>
    <h3>技能 (${((e=t.skills)==null?void 0:e.length)||0})</h3>
    <ul class="detail-list">
      ${(t.skills||[]).map(a=>`<li>🔧 ${s(a.name)} ${a.enabled?"✅":"❌"}</li>`).join("")}
    </ul>
    <div class="actions">
      <button class="primary" id="btn-goto-chat">进入对话</button>
    </div>
  `,document.getElementById("btn-goto-chat").onclick=()=>{o.taskId?U(t.id,o.taskId):Z()}}async function qt(t){var c,h,y;let n=[];try{n=await mt()}catch{}const e=document.getElementById("tab-content");new Set(t.experts.filter(l=>l.enabled).map(l=>l.expertId));const a=t.coordinatorAgentId||"",i=t.experts.filter(l=>l.expertId!==a);n.filter(l=>l.id!==a);const d=t.experts.some(l=>l.expertId===a&&a);e.innerHTML=`
    <h2>项目配置</h2>

    <h3>基本信息</h3>
    <div class="form-row">
      <label>名称 <input id="cfg-name" value="${s(t.name)}"></label>
      <label>描述 <input id="cfg-desc" value="${s(t.description||"")}"></label>
      <label>主 Agent (Coordinator)
        <select id="cfg-coordinator">
          <option value="">使用全局默认</option>
          ${n.map(l=>`<option value="${s(l.id)}" ${l.id===a?"selected":""}>${s(l.name)} (${s(l.id)})</option>`).join("")}
        </select>
      </label>
      <button class="primary" id="btn-save-info">保存</button>
    </div>

    ${a?`
    <h3>主 Agent</h3>
    <div class="expert-card coordinator-card">
      <div class="expert-name">⭐ ${s(((c=n.find(l=>l.id===a))==null?void 0:c.name)||a)}</div>
      <div class="expert-id">${s(a)}</div>
      <div class="expert-desc">${s(((h=n.find(l=>l.id===a))==null?void 0:h.description)||"")}</div>
    </div>
    `:""}

    <h3>专家团队 ${d?'<span class="hint" style="color:var(--danger)">⚠ 主Agent不能同时在专家团队中，请先移除以避免保存失败</span>':""}</h3>
    ${i.length===0?'<p class="hint">暂无专家</p>':""}
    <div class="expert-grid">
      ${i.map(l=>{const u=n.find(v=>v.id===l.expertId);return`
        <div class="expert-card added">
          <div class="expert-name">${s((u==null?void 0:u.name)||l.expertId)}</div>
          <div class="expert-id">${s(l.expertId)}</div>
          <div class="expert-desc">${s((u==null?void 0:u.description)||"")}</div>
          <div class="expert-actions">
            <label><input type="checkbox" class="toggle-expert" data-expert="${s(l.expertId)}" ${l.enabled?"checked":""}> 启用</label>
            <button class="small danger btn-remove-expert" data-expert="${s(l.expertId)}">移除</button>
          </div>
        </div>`}).join("")}
    </div>
    <button class="primary" id="btn-add-expert">+ 添加专家</button>

    <h3>技能 ${(y=t.skills)!=null&&y.length?"":'<span class="hint">（仅平台内置AgentCore支持）</span>'}</h3>
    ${(t.skills||[]).length===0?'<p class="hint">暂无技能</p>':""}
    <div class="expert-grid" id="skill-grid">
      ${(t.skills||[]).map(l=>`
        <div class="expert-card added">
          <div class="expert-name">🔧 ${s(l.name)}</div>
          <div class="expert-id">${s(l.id)}</div>
          <div class="expert-desc">${s(l.description||"")}</div>
          <div class="expert-actions">
            <label><input type="checkbox" class="toggle-skill" data-skill="${s(l.id)}" ${l.enabled?"checked":""}> 启用</label>
            <button class="small danger btn-remove-skill" data-skill="${s(l.id)}">移除</button>
          </div>
        </div>`).join("")}
    </div>
    <button class="primary" id="btn-add-skill">+ 添加技能</button>

    <h3>成员</h3>
    <div id="member-list">
      ${t.members.map(l=>`
        <div class="member-row">
          <span>${s(l.userId)}</span>
          ${l.role!=="OWNER"?`<select class="role-select" data-user="${s(l.userId)}">
                <option value="ADMIN" ${l.role==="ADMIN"?"selected":""}>ADMIN</option>
                <option value="MEMBER" ${l.role==="MEMBER"?"selected":""}>MEMBER</option>
                <option value="VIEWER" ${l.role==="VIEWER"?"selected":""}>VIEWER</option>
              </select>
              <button class="small danger btn-remove-member" data-user="${s(l.userId)}">移除</button>`:'<span class="role-tag">OWNER</span>'}
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
  `,document.getElementById("btn-save-info").onclick=async()=>{const l=r("cfg-name").value.trim(),u=r("cfg-desc").value.trim(),v=r("cfg-coordinator").value.trim();if(l)try{await rt(t.id,{name:l,description:u,coordinatorAgentId:v}),b=await f(t.id),I("settings")}catch(p){alert(String(p))}},document.getElementById("btn-add-expert").onclick=()=>{const l=new Set(t.experts.map(p=>p.expertId)),u=t.coordinatorAgentId||"",v=n.filter(p=>!l.has(p.id)&&p.id!==u);r("dialog-overlay").innerHTML=`
      <div class="dialog wide">
        <h3>添加专家</h3>
        ${v.length===0?"<p>所有专家已添加</p>":""}
        <div style="max-height:300px;overflow-y:auto">
        ${v.map(p=>`
          <div class="expert-card" style="cursor:pointer" data-add-expert="${s(p.id)}">
            <div class="expert-name">${s(p.name)}</div>
            <div class="expert-id">${s(p.id)}</div>
            <div class="expert-desc">${s(p.description||"")}</div>
            <div class="expert-caps">${(p.capabilities||[]).map(w=>`<span class="cap-tag">${s(w)}</span>`).join(" ")}</div>
          </div>
        `).join("")}
        </div>
        <div class="dialog-actions">
          <button id="dlg-cancel">取消</button>
        </div>
      </div>`,r("dialog-overlay").style.display="flex",r("dlg-cancel").onclick=()=>r("dialog-overlay").style.display="none",document.querySelectorAll("[data-add-expert]").forEach(p=>{p.addEventListener("click",async()=>{const w=p.dataset.addExpert;try{await J(t.id,w,!0),b=await f(t.id),r("dialog-overlay").style.display="none",I("settings")}catch(T){alert(String(T))}})})},e.querySelectorAll(".toggle-expert").forEach(l=>{l.addEventListener("change",async()=>{const u=l.dataset.expert,v=l.checked;try{await J(t.id,u,v),b=await f(t.id)}catch(p){alert(String(p))}})}),e.querySelectorAll(".btn-remove-expert").forEach(l=>{l.addEventListener("click",async()=>{const u=l.dataset.expert;try{await vt(t.id,u),b=await f(t.id),I("settings")}catch(v){alert(String(v))}})}),document.getElementById("btn-add-skill").onclick=()=>{const l=new Set((t.skills||[]).map(u=>u.id));ht().then(u=>{const v=u.filter(p=>!l.has(p.id));r("dialog-overlay").innerHTML=`
        <div class="dialog wide">
          <h3>添加技能</h3>
          ${v.length===0?"<p>所有技能已添加</p>":""}
          <div style="max-height:300px;overflow-y:auto">
          ${v.map(p=>`
            <div class="expert-card" style="cursor:pointer" data-add-skill="${s(p.id)}">
              <div class="expert-name">🔧 ${s(p.name)}</div>
              <div class="expert-id">${s(p.id)}</div>
              <div class="expert-desc">${s(p.description||"")}</div>
            </div>
          `).join("")}
          </div>
          <div class="dialog-actions">
            <button id="dlg-cancel">取消</button>
          </div>
        </div>`,r("dialog-overlay").style.display="flex",r("dlg-cancel").onclick=()=>r("dialog-overlay").style.display="none",document.querySelectorAll("[data-add-skill]").forEach(p=>{p.addEventListener("click",async()=>{const w=p.dataset.addSkill;try{await K(t.id,w,!0),b=await f(t.id),r("dialog-overlay").style.display="none",I("settings")}catch(T){alert(String(T))}})})}).catch(u=>alert(String(u)))},e.querySelectorAll(".toggle-skill").forEach(l=>{l.addEventListener("change",async()=>{const u=l.dataset.skill,v=l.checked;try{await K(t.id,u,v),b=await f(t.id)}catch(p){alert(String(p))}})}),e.querySelectorAll(".btn-remove-skill").forEach(l=>{l.addEventListener("click",async()=>{const u=l.dataset.skill;try{await bt(t.id,u),b=await f(t.id),I("settings")}catch(v){alert(String(v))}})}),document.getElementById("btn-add-member").onclick=async()=>{const l=r("new-member-id").value.trim(),u=r("new-member-role").value;if(l)try{await _(t.id,l,u),b=await f(t.id),I("settings")}catch(v){alert(String(v))}},e.querySelectorAll(".role-select").forEach(l=>{l.addEventListener("change",async()=>{const u=l.dataset.user,v=l.value;try{await _(t.id,u,v),b=await f(t.id),I("settings")}catch(p){alert(String(p))}})}),e.querySelectorAll(".btn-remove-member").forEach(l=>{l.addEventListener("click",async()=>{const u=l.dataset.user;try{await yt(t.id,u),b=await f(t.id),I("settings")}catch(v){alert(String(v))}})})}function U(t,n){var e;(e=o.stream)==null||e.disconnect(),o.projectId=t,o.taskId=n,o.pendingHumanRequest=null,$(),g(),tt()}async function tt(){if(!o.projectId||!o.taskId){x();return}k=null,r("main-content").innerHTML=`
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
  `;const t=r("chat-input");t.addEventListener("input",()=>{t.style.height="auto",t.style.height=Math.min(t.scrollHeight,120)+"px"}),t.addEventListener("keydown",n=>{n.key==="Enter"&&!n.shiftKey&&(n.preventDefault(),r("chat-form").requestSubmit())});try{const n=await lt(o.projectId,o.taskId),e=o.tasks.get(o.projectId)||[];e.find(i=>i.taskId===n.taskId)||(e.push(n),o.tasks.set(o.projectId,e),g());const a=document.getElementById("chat-title");a&&(a.textContent=n.title)}catch(n){const e=(n==null?void 0:n.message)||String(n);if(e.includes("VIEWER")||e.includes("FORBIDDEN")||e.includes("403")){o.taskId="",$(),r("main-content").innerHTML=`
        <div class="panel" style="margin-top:60px;text-align:center">
          <h3>权限不足</h3>
          <p>你当前是 VIEWER 角色，只能查看项目，不能进入对话。</p>
          <button id="btn-back-project" class="primary" style="margin-top:12px">返回项目</button>
        </div>
      `,document.getElementById("btn-back-project").onclick=()=>M(o.projectId),g();return}const a=document.getElementById("chat-title");a&&(a.textContent="对话")}Rt(),r("chat-form").addEventListener("submit",async n=>{n.preventDefault();const e=t.value.trim();if(e){t.value="",t.style.height="auto";try{await pt(o.projectId,o.taskId,e)}catch(a){Ut("error",String(a),"system")}}})}function Rt(){var e;(e=o.stream)==null||e.disconnect(),o.stream=new Lt(o.projectId,o.taskId),o.stream.setLastSequence(0),k=null;const t=document.getElementById("chat-timeline");t&&(t.innerHTML=""),o.stream.onEvent(a=>{Wt(a)}),o.stream.connect();const n=document.getElementById("connection-status");n&&(n.className="dot online",n.textContent="已连接")}function Ut(t,n,e){const a=document.getElementById("chat-timeline");a&&(a.insertAdjacentHTML("beforeend",`<div class="bubble ${t}">
      <div class="meta">${s(e)} · ${C(Date.now())}</div>
      <div class="text">${s(n)}</div>
    </div>`),a.scrollTop=a.scrollHeight)}let k=null,N="";function Wt(t){const n=document.getElementById("chat-timeline");if(!n)return;const e=t.type||"";if((e==="confirm"||e==="coordinatorConfirm")&&(o.pendingHumanRequest={id:t.questionId||"",question:t.content||"Agent 需要你的确认",type:"CLARIFICATION",status:"PENDING"},Kt(t)),e==="userMessage"){k=null,N="";const c=t.agentId||"user",h=c===j().user;n.insertAdjacentHTML("beforeend",`<div class="bubble ${h?"user":"other"}">
        <div class="meta">${s(c)} · ${C(t.timestamp)}</div>
        <div class="text">${s(t.content||"")}</div>
      </div>`),n.scrollTop=n.scrollHeight;return}let a="";if(e==="thinkingDelta"||e==="thinking")a=t.text||"";else if(e==="textDelta")a=t.text||"";else if(e==="chat"||e==="coordinatorChat")a=t.content||"";else if(e==="end")(t.content||"").startsWith("{")||(a=t.content||"");else if(e==="coordinatorPhase"||e==="liveStatus"||e==="coordinatorPlanUpdate"||e==="coordinatorNewPlanStep"||e==="coordinatorError"||e==="planUpdate"||e==="newPlanStep"||e==="error"||e==="taskInQueue"){const c=_t({type:e,...t});if(a=t.content||t.text||c||"",!a&&(e==="coordinatorPlanUpdate"||e==="planUpdate")){const h=t.tasks;h!=null&&h.length&&(a="计划: "+h.map(y=>y.title).join(", "))}}const i=t.attachments||[];if(!a&&i.length===0)return;t.agentId&&t.agentId!==N&&(N=t.agentId),k||(k=document.createElement("div"),k.className="bubble system",k.innerHTML=`
      <div class="reply-agent">${s(N)}</div>
      <div class="reply-output"></div>
    `,n.appendChild(k));const d=k.querySelector(".reply-output");e==="thinkingDelta"||e==="thinking"||e==="textDelta"?d.textContent+=a:e==="chat"||e==="coordinatorChat"||e==="end"&&!(t.content||"").startsWith("{")?(d.textContent&&(d.textContent+=`

`),d.textContent+=a):(d.textContent&&(d.textContent+=`
`),d.textContent+="["+C(t.timestamp)+"] "+a),i.length>0&&Jt(k,i),n.scrollTop=n.scrollHeight}function Jt(t,n){const e=t.querySelector(".reply-output"),a=document.createElement("div");a.className="attachments";for(const i of n){const d=i.path||"",c=i.fileName||d||"attachment",h=document.createElement("a");h.className="attachment-item",h.textContent="📎 "+c,h.title="点击预览/下载",h.onclick=async y=>{if(y.preventDefault(),!!d)try{const l=await gt(o.projectId,d);if(!l.downloadUrl){h.textContent="📎 "+c+"(未完成上传)";return}window.open(l.downloadUrl,"_blank")}catch{h.textContent="📎 "+c+"(不可用)"}},a.appendChild(h)}e.appendChild(a)}function _t(t){const n=t,e=n.type||"",a=n.status||"",i=n.content||n.text||"",d={userMessage:"用户消息",coordinatorPhase:"阶段",coordinatorChat:"回复",coordinatorConfirm:"需要确认",coordinatorError:"错误",coordinatorPlanUpdate:"计划更新",coordinatorNewPlanStep:"任务开始",coordinatorRunCancelled:"已取消",liveStatus:"",thinkingStart:"开始思考",thinkingDelta:"思考中",thinking:"思考",thinkingEnd:"思考结束",textDelta:"输出中",streamStart:"开始输出",streamEnd:"输出结束",chat:"回复",end:"完成",error:"错误",confirm:"需要确认",planUpdate:"计划更新",newPlanStep:"任务开始",taskInQueue:"排队中",toolUsed:"工具调用",toolResult:"工具结果",weblink:"打开链接",file:"文件",directory:"目录",sidebarDisplay:"工作区",clearBoundary:"上下文清理",compactBoundary:"上下文压缩",reconnect:"重连"};return e==="coordinatorPhase"?{analyzing:"正在理解需求",planning:"正在制定计划",dispatching:"正在分配专家",answering:"正在整理回复",waiting_human:"需要确认",completed:"已完成",failed:"执行失败"}[a]||i||"阶段转换":e==="liveStatus"?i||"状态更新":d[e]||i||null}function Kt(t){const n=document.getElementById("hitl-panel");if(!n)return;const e=t.content||"Agent 需要你的确认",a=t.questions;n.className="hitl-panel",a!=null&&a.length?(n.innerHTML=a.map(i=>{var d;return`
      <div class="hitl-question">
        <strong>${s(i.header||i.question)}</strong>
        <p>${s(i.question)}</p>
        ${(d=i.options)!=null&&d.length?`<div>${i.options.map(c=>`<label class="hitl-option"><input type="${i.multiSelect?"checkbox":"radio"}" name="hitl-${s(i.header)}" value="${s(c.label)}"> ${s(c.label)} — ${s(c.description)}</label>`).join("")}</div>`:`<input class="hitl-input" data-question="${s(i.question)}" placeholder="输入你的回答...">`}
      </div>`}).join("")+'<button class="primary" id="hitl-submit">提交回答</button>',r("hitl-submit").onclick=async()=>{const i={};n.querySelectorAll(".hitl-input").forEach(d=>{const c=d;i[c.dataset.question||"answer"]=c.value.trim()}),n.querySelectorAll("input:checked").forEach(d=>{i[d.name.replace("hitl-","")]=d.value}),await F(i)}):(n.innerHTML=`
      <strong>${s(e)}</strong>
      <input id="hitl-answer" placeholder="输入回答...">
      <button class="primary" id="hitl-submit">提交</button>
    `,r("hitl-submit").onclick=async()=>{const i=r("hitl-answer").value.trim();i&&await F({answer:i})})}async function F(t){const n=o.pendingHumanRequest;if(n)try{await ut(o.projectId,n.id,"ANSWER",t),o.pendingHumanRequest=null;const e=document.getElementById("hitl-panel");e&&(e.className="hitl-panel hidden")}catch(e){alert(String(e))}}document.addEventListener("DOMContentLoaded",Mt);function Vt(){const t=r("dialog-overlay");t.innerHTML=`
    <div class="dialog admin">
      <h3>租户管理</h3>
      <div id="admin-tenant-body">加载中…</div>
      <div class="dialog-actions">
        <button id="dlg-admin-close">关闭</button>
      </div>
    </div>
  `,t.style.display="flex",r("dlg-admin-close").onclick=()=>t.style.display="none",S().then(A).catch(async n=>{try{const e=await q(),{tenant:a}=j(),i=e.find(d=>d.tenantId===a);if(i&&i.role==="TENANT_ADMIN"){r("admin-tenant-body").innerHTML=`
            <p>你不是平台管理员(平台管理员由 PLATFORM_ADMIN_USERS 配置,默认 root);
            以下为当前租户 <b>${s(i.name)}</b> 的成员管理。</p>
            <div id="admin-members-panel"></div>`,D(a);return}}catch{}r("admin-tenant-body").textContent="无权限:既不是平台管理员,也不是当前租户的管理员。",alert(String(n))})}function A(t){const n=t.map(e=>`
    <div class="admin-tenant-row">
      <span class="admin-tenant-name">${s(e.name)}
        <small>${s(e.tenantId)}</small></span>
      <span class="admin-tenant-meta">负责人 ${s(e.ownerUserId)} · ${s(e.status)}${e.description?` · ${s(e.description)}`:""}</span>
      <button data-action="admin-edit" data-tenant="${s(e.tenantId)}">编辑</button>
      <button data-action="admin-members" data-tenant="${s(e.tenantId)}">成员</button>
      <button data-action="admin-disable" data-tenant="${s(e.tenantId)}">禁用</button>
      <button data-action="admin-delete" data-tenant="${s(e.tenantId)}">删除</button>
    </div>`).join("");r("admin-tenant-body").innerHTML=`
    <div class="admin-create">
      <input id="admin-new-name" placeholder="租户名称">
      <input id="admin-new-desc" placeholder="描述（可选）">
      <input id="admin-new-owner" placeholder="负责人 userId">
      <button class="primary" id="admin-create-btn">创建租户</button>
    </div>
    ${n||"<p>暂无租户</p>"}
    <div id="admin-members-panel"></div>
  `,r("admin-create-btn").onclick=async()=>{const e=r("admin-new-name").value.trim(),a=r("admin-new-desc").value.trim(),i=r("admin-new-owner").value.trim();if(!(!e||!i))try{await kt(e,i,a||void 0),A(await S())}catch(d){alert(String(d))}},document.querySelectorAll("[data-action=admin-disable]").forEach(e=>e.addEventListener("click",async()=>{const a=e.dataset.tenant;if(confirm(`禁用租户 ${a}？`))try{await wt(a),A(await S())}catch(i){alert(String(i))}})),document.querySelectorAll("[data-action=admin-delete]").forEach(e=>e.addEventListener("click",async()=>{const a=e.dataset.tenant;if(confirm(`删除租户 ${a}？（仅限无项目的租户）`))try{await Et(a),A(await S())}catch(i){alert(String(i))}})),document.querySelectorAll("[data-action=admin-members]").forEach(e=>e.addEventListener("click",()=>D(e.dataset.tenant))),document.querySelectorAll("[data-action=admin-edit]").forEach(e=>e.addEventListener("click",()=>{const a=e.dataset.tenant,i=t.find(d=>d.tenantId===a);i&&Ft(i)}))}function Ft(t){const n=r("dialog-overlay");n.innerHTML=`
    <div class="dialog wide">
      <h3>编辑租户 ${s(t.name)}</h3>
      <label>名称 <input id="te-name" value="${s(t.name)}"></label>
      <label>描述 <input id="te-desc" value="${s(t.description??"")}"></label>
      <label>负责人 userId <input id="te-owner" value="${s(t.ownerUserId)}"></label>
      <div class="dialog-actions">
        <button class="primary" id="te-save">保存</button>
        <button id="te-cancel">取消</button>
      </div>
    </div>
  `,n.style.display="flex",r("te-cancel").onclick=()=>n.style.display="none",r("te-save").onclick=async()=>{const e=r("te-name").value.trim(),a=r("te-owner").value.trim();if(!(!e||!a))try{await jt(t.tenantId,{name:e,description:r("te-desc").value.trim(),ownerUserId:a}),n.style.display="none",A(await S())}catch(i){alert(String(i))}}}function D(t){const n=r("admin-members-panel");n.innerHTML="<p>成员加载中…</p>",ft(t).then(e=>{const a=e.map(i=>`
      <div class="admin-member-row">
        <span>${s(i.userId)} · ${s(i.role)}</span>
        <button data-action="admin-rm-member" data-user="${s(i.userId)}">移除</button>
      </div>`).join("");n.innerHTML=`
      <h4>成员 · ${s(t)}</h4>
      <div class="admin-create">
        <input id="admin-mem-user" placeholder="userId">
        <select id="admin-mem-role">
          <option value="MEMBER">MEMBER</option>
          <option value="TENANT_ADMIN">TENANT_ADMIN</option>
        </select>
        <button class="primary" id="admin-mem-add">赋权</button>
      </div>
      ${a||"<p>暂无成员</p>"}`,r("admin-mem-add").onclick=async()=>{const i=r("admin-mem-user").value.trim(),d=r("admin-mem-role").value;if(i)try{await It(t,i,d),D(t)}catch(c){alert(String(c))}},n.querySelectorAll("[data-action=admin-rm-member]").forEach(i=>i.addEventListener("click",async()=>{const d=i.dataset.user;if(confirm(`移除成员 ${d}？`))try{await $t(t,d),D(t)}catch(c){alert(String(c))}}))}).catch(e=>{n.innerHTML="<p>成员加载失败</p>",alert(String(e))})}function Xt(){r("main-content").innerHTML=`
    <div class="admin-page">
      <div class="page-header">
        <h2>系统管理</h2>
        <button id="btn-admin-back">← 返回</button>
      </div>
      <section class="admin-section">
        <h3>租户管理</h3>
        <div id="admin-tenant-body">加载中…</div>
        <div id="admin-members-panel"></div>
      </section>
      <section class="admin-section">
        <h3>系统提示词</h3>
        <div id="admin-prompt-body">加载中…</div>
      </section>
    </div>
  `,r("btn-admin-back").onclick=x,S().then(A).catch(t=>{r("admin-tenant-body").textContent="租户列表加载失败",alert(String(t))}),H()}function H(){St().then(t=>{const n=new Map;for(const a of t){const i=n.get(a.promptKey)||[];i.push(a),n.set(a.promptKey,i)}const e=Array.from(n.entries()).map(([a,i])=>{const d=[...i].sort((y,l)=>l.version-y.version),c=d[0],h=d.map(y=>`
        <div class="prompt-version-row">
          <span>v${y.version} · ${s(y.scene)} · ${s(y.status)}${y.id===c.id?" · 最新":""}</span>
          <button data-action="prompt-edit" data-id="${s(y.id)}">编辑</button>
          ${y.status!=="PUBLISHED"?`<button data-action="prompt-publish" data-id="${s(y.id)}">发布</button>`:""}
          ${y.status!=="PUBLISHED"?`<button data-action="prompt-delete" data-id="${s(y.id)}">删除</button>`:""}
        </div>`).join("");return`
        <div class="prompt-group">
          <div class="prompt-row">
            <span class="prompt-key">${s(a)}
              <small>${i.length} 个版本 · 最新 v${c.version}(${s(c.status)})</small></span>
            <button data-action="prompt-toggle" data-key="${s(a)}">版本</button>
            <button data-action="prompt-edit" data-id="${s(c.id)}">编辑(新版本)</button>
          </div>
          <div class="prompt-versions" data-versions="${s(a)}" style="display:none">
            ${h}
          </div>
        </div>`}).join("");r("admin-prompt-body").innerHTML=`
        <button class="primary" id="btn-prompt-new">新建模板</button>
        ${e||"<p>暂无模板</p>"}`,r("btn-prompt-new").onclick=()=>X(null),document.querySelectorAll("[data-action=prompt-toggle]").forEach(a=>a.addEventListener("click",()=>{const i=a.dataset.key,d=document.querySelector(`.prompt-versions[data-versions="${CSS.escape(i)}"]`);d&&(d.style.display=d.style.display==="none"?"block":"none")})),document.querySelectorAll("[data-action=prompt-edit]").forEach(a=>a.addEventListener("click",()=>X(t.find(i=>i.id===a.dataset.id)||null))),document.querySelectorAll("[data-action=prompt-publish]").forEach(a=>a.addEventListener("click",async()=>{const i=a.dataset.id;if(confirm("发布该版本？发布后运行时立即使用。"))try{await Tt(i),H()}catch(d){alert(String(d))}})),document.querySelectorAll("[data-action=prompt-delete]").forEach(a=>a.addEventListener("click",async()=>{const i=a.dataset.id;if(confirm("删除该版本？其渲染审计记录也会一并删除。"))try{await At(i),H()}catch(d){alert(String(d))}}))}).catch(t=>{r("admin-prompt-body").textContent="提示词列表加载失败",alert(String(t))})}function X(t){const n=r("dialog-overlay"),e=t;n.innerHTML=`
    <div class="dialog wide">
      <h3>${e?`编辑 ${s(e.promptKey)}(保存为新版本)`:"新建模板"}</h3>
      <label>promptKey
        <input id="pe-key" value="${s((e==null?void 0:e.promptKey)??"")}" placeholder="coordinator.execution"></label>
      <label>agentScope
        <input id="pe-scope" value="${s((e==null?void 0:e.agentScope)??"COORDINATOR")}"></label>
      <label>scene
        <input id="pe-scene" value="${s((e==null?void 0:e.scene)??"")}"></label>
      <label>模板内容
        <textarea id="pe-content" rows="14">${s((e==null?void 0:e.templateContent)??"")}</textarea></label>
      <label>variablesSchema
        <input id="pe-vars" value="${s((e==null?void 0:e.variablesSchema)??'{"required":["context_json"]}')}"></label>
      <div class="dialog-actions">
        <button class="primary" id="pe-save">保存为新版本</button>
        <button id="pe-cancel">取消</button>
      </div>
    </div>
  `,n.style.display="flex",r("pe-cancel").onclick=()=>n.style.display="none",r("pe-save").onclick=async()=>{const a=r("pe-key").value.trim(),i=r("pe-content").value;if(!(!a||!i))try{await xt({promptKey:a,agentScope:r("pe-scope").value.trim()||"COORDINATOR",scene:r("pe-scene").value.trim()||a,templateContent:i,variablesSchema:r("pe-vars").value.trim()}),n.style.display="none",H()}catch(d){alert(String(d))}}}
