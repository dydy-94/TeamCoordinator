var K=Object.defineProperty;var Q=(t,e,n)=>e in t?K(t,e,{enumerable:!0,configurable:!0,writable:!0,value:n}):t[e]=n;var L=(t,e,n)=>Q(t,typeof e!="symbol"?e+"":e,n);(function(){const e=document.createElement("link").relList;if(e&&e.supports&&e.supports("modulepreload"))return;for(const r of document.querySelectorAll('link[rel="modulepreload"]'))a(r);new MutationObserver(r=>{for(const d of r)if(d.type==="childList")for(const p of d.addedNodes)p.tagName==="LINK"&&p.rel==="modulepreload"&&a(p)}).observe(document,{childList:!0,subtree:!0});function n(r){const d={};return r.integrity&&(d.integrity=r.integrity),r.referrerPolicy&&(d.referrerPolicy=r.referrerPolicy),r.crossOrigin==="use-credentials"?d.credentials="include":r.crossOrigin==="anonymous"?d.credentials="omit":d.credentials="same-origin",d}function a(r){if(r.ep)return;r.ep=!0;const d=n(r);fetch(r.href,d)}})();let D=localStorage.getItem("tenant")||"demo-tenant",B=localStorage.getItem("user")||"demo-owner";function z(t,e){D=t,B=e,localStorage.setItem("tenant",t),localStorage.setItem("user",e)}function A(){return{tenant:D,user:B}}function G(){return{"X-Tenant-Id":D,"X-User-Id":B,"Content-Type":"application/json"}}async function g(t,e={}){const n=await fetch(t,{...e,headers:{...G(),...e.headers||{}}});if(!n.ok){const a=await n.json().catch(()=>({message:n.statusText}));throw new Error(a.message||`HTTP ${n.status}`)}return n.status===204?null:n.json()}function Y(){return g("/api/v1/projects")}function y(t){return g(`/api/v1/projects/${t}`)}function Z(t,e){return g("/api/v1/projects",{method:"POST",body:JSON.stringify({name:t,description:e||""})})}function tt(t,e){return g(`/api/v1/projects/${t}`,{method:"PATCH",body:JSON.stringify(e)})}function et(t,e){return g(`/api/v1/projects/${t}/tasks`,{method:"POST",body:JSON.stringify({title:e})})}function nt(t,e){return g(`/api/v1/projects/${t}/tasks/${e}`)}function at(t,e){return g(`/api/v1/projects/${t}/tasks/${e}`,{method:"DELETE"})}function it(t,e,n){const a=crypto.randomUUID();return g(`/api/v1/projects/${t}/tasks/${e}/messages`,{method:"POST",body:JSON.stringify({client_message_id:a,text:n,attachment_refs:[],idempotency_key:a})})}function st(t,e,n,a){return g(`/api/v1/projects/${t}/human-requests/${e}/responses`,{method:"POST",body:JSON.stringify({decision:n,response:a,idempotencyKey:crypto.randomUUID()})})}function rt(){return g("/api/v1/experts")}function S(t){return g(`/api/v1/projects/${t}/tasks`)}function N(t,e,n){return g(`/api/v1/projects/${t}/experts`,{method:"POST",body:JSON.stringify({expertId:e,enabled:n})})}function ot(t,e){return g(`/api/v1/projects/${t}/experts/${e}`,{method:"DELETE"})}function q(t,e,n){return g(`/api/v1/projects/${t}/members`,{method:"POST",body:JSON.stringify({userId:e,role:n})})}function lt(t,e){return g(`/api/v1/projects/${t}/members/${e}`,{method:"DELETE"})}function dt(){return g("/api/v1/skills")}function O(t,e,n=!0){return g(`/api/v1/projects/${t}/skills`,{method:"POST",body:JSON.stringify({skillId:e,enabled:n})})}function ct(t,e){return g(`/api/v1/projects/${t}/skills/${e}`,{method:"DELETE"})}class ut{constructor(e,n){L(this,"abort",null);L(this,"lastSequence",0);L(this,"listeners",[]);this.projectId=e,this.taskId=n}onEvent(e){this.listeners.push(e)}setLastSequence(e){this.lastSequence=e}async connect(){var r;(r=this.abort)==null||r.abort();const e=new AbortController;this.abort=e;const{tenant:n,user:a}=A();for(;;)try{const d=await fetch(`/api/v1/projects/${this.projectId}/tasks/${this.taskId}/events`,{headers:{"X-Tenant-Id":n,"X-User-Id":a,"Last-Event-ID":String(this.lastSequence)},signal:e.signal});if(!d.ok)throw new Error(`SSE connect failed: ${d.status}`);const p=d.body.getReader(),x=new TextDecoder;let k="";for(;;){const{done:s,value:u}=await p.read();if(s)break;k+=x.decode(u,{stream:!0});let m;for(;(m=k.indexOf(`

`))>=0;){const c=k.slice(0,m);k=k.slice(m+2);const E=c.split(`
`).find($=>$.startsWith("id:")),w=E?Number(E.slice(3).trim()):Number.NaN,C=c.split(`
`).filter($=>$.startsWith("data:")).map($=>$.slice(5).trim()).join("");if(C)try{const $=JSON.parse(C);Number.isFinite(w)&&(this.lastSequence=Math.max(this.lastSequence,w));for(const X of this.listeners)try{X($)}catch{}}catch{}}}await R(1e3)}catch(d){if(d instanceof DOMException&&d.name==="AbortError")return;await R(2e3)}}disconnect(){var e;(e=this.abort)==null||e.abort()}}function R(t){return new Promise(e=>setTimeout(e,t))}const i={projectId:localStorage.getItem("projectId")||"",taskId:localStorage.getItem("taskId")||"",stream:null,projects:[],tasks:new Map,pendingHumanRequest:null};function f(){localStorage.setItem("projectId",i.projectId),localStorage.setItem("taskId",i.taskId)}const l=t=>document.getElementById(t),o=t=>String(t??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");function P(t){return t?new Date(t).toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"}):""}async function pt(){const t=document.getElementById("app");t.innerHTML=`
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
  `,mt(),V();try{if(i.projects=await Y(),i.projectId)try{const e=await S(i.projectId);i.tasks.set(i.projectId,e)}catch{}}catch{}h(),i.projectId&&i.taskId?H(i.projectId,i.taskId):i.projectId?T(i.projectId):j()}function mt(){W(),J()}function W(){const t=document.getElementById("sidebar-tree"),e=[];for(const n of i.projects){i.projectId,n.id;const a=i.tasks.get(n.id)||[],r=i.projectId===n.id;e.push(`
      <div class="tree-folder ${r?"active":""}">
        <div class="tree-row project-row"
             data-action="select-project"
             data-project="${o(n.id)}">
          <span class="tree-arrow" data-action="toggle-expand" data-project="${o(n.id)}">${r?"▼":"▶"}</span>
          <span class="tree-icon">📁</span>
          <span class="tree-label">${o(n.name)}</span>
        </div>
        ${r?gt(n.id,a):""}
      </div>
    `)}e.length===0&&e.push('<div class="tree-empty">暂无项目<br><small>点击下方按钮创建</small></div>'),t.innerHTML=e.join(""),vt()}function gt(t,e){return e.length===0?'<div class="tree-empty-sub">暂无任务</div>':e.map(n=>{const a=i.taskId===n.taskId;return`
      <div class="tree-row task-row ${a?"active":""}"
           data-action="select-task"
           data-project="${o(t)}"
           data-task="${o(n.taskId)}">
        <span class="tree-icon">💬</span>
        <span class="tree-label">${o(n.title)}</span>
        ${a?'<span class="tree-badge">●</span>':""}
        <button class="tree-del" data-action="delete-task" data-project="${o(t)}" data-task="${o(n.taskId)}" title="删除">×</button>
      </div>`}).join("")}function J(){const t=document.getElementById("sidebar-actions");t.innerHTML=`
    <button id="btn-new-project" class="sidebar-btn">+ 新建项目</button>
    <button id="btn-add-project" class="sidebar-btn">🔗 加入已有项目</button>
    <button id="btn-new-task" class="sidebar-btn" ${i.projectId?"":"disabled"}>+ 新建任务</button>
  `,document.getElementById("btn-new-project").onclick=yt,document.getElementById("btn-add-project").onclick=bt,document.getElementById("btn-new-task").onclick=()=>{i.projectId&&_()}}function V(){const{tenant:t,user:e}=A(),n=document.getElementById("sidebar-identity");n.innerHTML=`
    <div class="identity-row">
      <span class="identity-label">${o(t)} / ${o(e)}</span>
      <button class="identity-btn" id="identity-edit-btn">⚙</button>
    </div>
  `,document.getElementById("identity-edit-btn").onclick=ht}function vt(){document.querySelectorAll(".tree-row").forEach(t=>{t.addEventListener("click",async e=>{const n=e.target,a=n.dataset.action||t.dataset.action,r=t.dataset.project;if(a==="delete-task"){e.stopPropagation();const d=n.dataset.task;confirm("确定删除这个任务吗？")&&at(r,d).then(async()=>{i.taskId===d&&(i.taskId="",f(),j());try{const p=await S(r);i.tasks.set(r,p)}catch{}h()}).catch(p=>alert(String(p)));return}if(a==="toggle-expand"){if(e.stopPropagation(),i.projectId===r)i.projectId="",i.taskId="",f(),h(),j();else{i.projectId=r,f();try{const d=await S(r);i.tasks.set(r,d)}catch{}h()}return}if(a==="select-project")T(r);else if(a==="select-task"){const d=t.dataset.task;H(r,d)}})})}function h(){W(),J()}function ht(){const{tenant:t,user:e}=A();l("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>身份设置</h3>
      <label>Tenant ID <input id="dlg-tenant" value="${o(t)}"></label>
      <label>User ID <input id="dlg-user" value="${o(e)}"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-identity-save">保存</button>
        <button id="dlg-identity-cancel">取消</button>
      </div>
    </div>
  `,l("dialog-overlay").style.display="flex",l("dlg-identity-cancel").onclick=()=>l("dialog-overlay").style.display="none",l("dlg-identity-save").onclick=()=>{z(l("dlg-tenant").value.trim(),l("dlg-user").value.trim()),l("dialog-overlay").style.display="none",V()}}function yt(){l("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建项目</h3>
      <label>名称 <input id="dlg-name" autofocus></label>
      <label>描述 <input id="dlg-desc"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,l("dialog-overlay").style.display="flex",l("dlg-cancel").onclick=()=>l("dialog-overlay").style.display="none",l("dlg-create").onclick=async()=>{const t=l("dlg-name").value.trim();if(t)try{const e=await Z(t,l("dlg-desc").value.trim());i.projects.unshift(e),i.projectId=e.id,i.taskId="",f(),l("dialog-overlay").style.display="none",h(),T(e.id)}catch(e){alert(String(e))}}}function bt(){l("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>加入已有项目</h3>
      <label>项目 ID <input id="dlg-project-id" autofocus placeholder="粘贴项目 UUID"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-add">加入</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,l("dialog-overlay").style.display="flex",l("dlg-cancel").onclick=()=>l("dialog-overlay").style.display="none",l("dlg-add").onclick=async()=>{const t=l("dlg-project-id").value.trim();if(t)try{const e=await y(t);i.projects.find(n=>n.id===t)||i.projects.unshift(e),i.projectId=t,i.taskId="",f(),l("dialog-overlay").style.display="none",h(),T(t)}catch(e){alert(String(e))}}}function _(){l("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建任务</h3>
      <label>标题 <input id="dlg-title" value="新对话" autofocus></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,l("dialog-overlay").style.display="flex",l("dlg-cancel").onclick=()=>l("dialog-overlay").style.display="none",l("dlg-create").onclick=async()=>{const t=l("dlg-title").value.trim()||"新对话";try{const e=await et(i.projectId,t);i.taskId=e.taskId,f(),l("dialog-overlay").style.display="none";try{const n=await S(i.projectId);i.tasks.set(i.projectId,n)}catch{}h(),F()}catch(e){alert(String(e))}}}function j(){l("main-content").innerHTML=`
    <div class="panel welcome-panel">
      <h2>TeamCoordinator</h2>
      <p>AI Agent 编排服务测试前端</p>
      <p class="hint">从左侧创建或选择一个项目开始</p>
    </div>
  `}let v=null;async function T(t){i.projectId=t,f(),h();try{v=await y(t);const e=i.projects.findIndex(a=>a.id===t);e>=0?i.projects[e]=v:i.projects.push(v);const n=await S(t);i.tasks.set(t,n),h(),b("overview")}catch(e){l("main-content").innerHTML=`
      <div class="panel error">
        <p>项目加载失败: ${o(e)}</p>
        <button id="btn-remove-project">从列表中移除</button>
      </div>
    `,document.getElementById("btn-remove-project").onclick=()=>{i.projects=i.projects.filter(n=>n.id!==t),i.projectId="",i.taskId="",f(),h(),j()}}}async function b(t){const e=v;e&&(l("main-content").innerHTML=`
    <div class="panel">
      <div class="tabs">
        <button class="tab ${t==="overview"?"active":""}" id="tab-overview">概述</button>
        <button class="tab ${t==="settings"?"active":""}" id="tab-settings">配置</button>
      </div>
      <div id="tab-content"></div>
    </div>
  `,document.getElementById("tab-overview").onclick=()=>b("overview"),document.getElementById("tab-settings").onclick=()=>b("settings"),t==="overview"?ft(e):await It(e))}function ft(t){var n;const e=document.getElementById("tab-content");e.innerHTML=`
    <h2>${o(t.name)} <span class="badge">${o(t.status)}</span></h2>
    <p>${o(t.description||"无描述")}</p>
    <h3>成员 (${t.members.length})</h3>
    <ul class="detail-list">
      ${t.members.map(a=>`<li>${o(a.userId)} <span class="role-tag">${o(a.role)}</span></li>`).join("")}
    </ul>
    <h3>专家 (${t.experts.length})</h3>
    <ul class="detail-list">
      ${t.experts.map(a=>`<li>${o(a.expertId)} ${a.enabled?"✅":"❌"}</li>`).join("")}
    </ul>
    <h3>技能 (${((n=t.skills)==null?void 0:n.length)||0})</h3>
    <ul class="detail-list">
      ${(t.skills||[]).map(a=>`<li>🔧 ${o(a.name)} ${a.enabled?"✅":"❌"}</li>`).join("")}
    </ul>
    <div class="actions">
      <button class="primary" id="btn-goto-chat">进入对话</button>
    </div>
  `,document.getElementById("btn-goto-chat").onclick=()=>{i.taskId?H(t.id,i.taskId):_()}}async function It(t){var p,x,k;let e=[];try{e=await rt()}catch{}const n=document.getElementById("tab-content");new Set(t.experts.filter(s=>s.enabled).map(s=>s.expertId));const a=t.coordinatorAgentId||"",r=t.experts.filter(s=>s.expertId!==a);e.filter(s=>s.id!==a);const d=t.experts.some(s=>s.expertId===a&&a);n.innerHTML=`
    <h2>项目配置</h2>

    <h3>基本信息</h3>
    <div class="form-row">
      <label>名称 <input id="cfg-name" value="${o(t.name)}"></label>
      <label>描述 <input id="cfg-desc" value="${o(t.description||"")}"></label>
      <label>主 Agent (Coordinator)
        <select id="cfg-coordinator">
          <option value="">使用全局默认</option>
          ${e.map(s=>`<option value="${o(s.id)}" ${s.id===a?"selected":""}>${o(s.name)} (${o(s.id)})</option>`).join("")}
        </select>
      </label>
      <button class="primary" id="btn-save-info">保存</button>
    </div>

    ${a?`
    <h3>主 Agent</h3>
    <div class="expert-card coordinator-card">
      <div class="expert-name">⭐ ${o(((p=e.find(s=>s.id===a))==null?void 0:p.name)||a)}</div>
      <div class="expert-id">${o(a)}</div>
      <div class="expert-desc">${o(((x=e.find(s=>s.id===a))==null?void 0:x.description)||"")}</div>
    </div>
    `:""}

    <h3>专家团队 ${d?'<span class="hint" style="color:var(--danger)">⚠ 主Agent不能同时在专家团队中，请先移除以避免保存失败</span>':""}</h3>
    ${r.length===0?'<p class="hint">暂无专家</p>':""}
    <div class="expert-grid">
      ${r.map(s=>{const u=e.find(m=>m.id===s.expertId);return`
        <div class="expert-card added">
          <div class="expert-name">${o((u==null?void 0:u.name)||s.expertId)}</div>
          <div class="expert-id">${o(s.expertId)}</div>
          <div class="expert-desc">${o((u==null?void 0:u.description)||"")}</div>
          <div class="expert-actions">
            <label><input type="checkbox" class="toggle-expert" data-expert="${o(s.expertId)}" ${s.enabled?"checked":""}> 启用</label>
            <button class="small danger btn-remove-expert" data-expert="${o(s.expertId)}">移除</button>
          </div>
        </div>`}).join("")}
    </div>
    <button class="primary" id="btn-add-expert">+ 添加专家</button>

    <h3>技能 ${(k=t.skills)!=null&&k.length?"":'<span class="hint">（仅平台内置AgentCore支持）</span>'}</h3>
    ${(t.skills||[]).length===0?'<p class="hint">暂无技能</p>':""}
    <div class="expert-grid" id="skill-grid">
      ${(t.skills||[]).map(s=>`
        <div class="expert-card added">
          <div class="expert-name">🔧 ${o(s.name)}</div>
          <div class="expert-id">${o(s.id)}</div>
          <div class="expert-desc">${o(s.description||"")}</div>
          <div class="expert-actions">
            <label><input type="checkbox" class="toggle-skill" data-skill="${o(s.id)}" ${s.enabled?"checked":""}> 启用</label>
            <button class="small danger btn-remove-skill" data-skill="${o(s.id)}">移除</button>
          </div>
        </div>`).join("")}
    </div>
    <button class="primary" id="btn-add-skill">+ 添加技能</button>

    <h3>成员</h3>
    <div id="member-list">
      ${t.members.map(s=>`
        <div class="member-row">
          <span>${o(s.userId)}</span>
          ${s.role!=="OWNER"?`<select class="role-select" data-user="${o(s.userId)}">
                <option value="ADMIN" ${s.role==="ADMIN"?"selected":""}>ADMIN</option>
                <option value="MEMBER" ${s.role==="MEMBER"?"selected":""}>MEMBER</option>
                <option value="VIEWER" ${s.role==="VIEWER"?"selected":""}>VIEWER</option>
              </select>
              <button class="small danger btn-remove-member" data-user="${o(s.userId)}">移除</button>`:'<span class="role-tag">OWNER</span>'}
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
  `,document.getElementById("btn-save-info").onclick=async()=>{const s=l("cfg-name").value.trim(),u=l("cfg-desc").value.trim(),m=l("cfg-coordinator").value.trim();if(s)try{await tt(t.id,{name:s,description:u,coordinatorAgentId:m}),v=await y(t.id),b("settings")}catch(c){alert(String(c))}},document.getElementById("btn-add-expert").onclick=()=>{const s=new Set(t.experts.map(c=>c.expertId)),u=t.coordinatorAgentId||"",m=e.filter(c=>!s.has(c.id)&&c.id!==u);l("dialog-overlay").innerHTML=`
      <div class="dialog wide">
        <h3>添加专家</h3>
        ${m.length===0?"<p>所有专家已添加</p>":""}
        <div style="max-height:300px;overflow-y:auto">
        ${m.map(c=>`
          <div class="expert-card" style="cursor:pointer" data-add-expert="${o(c.id)}">
            <div class="expert-name">${o(c.name)}</div>
            <div class="expert-id">${o(c.id)}</div>
            <div class="expert-desc">${o(c.description)}</div>
          </div>
        `).join("")}
        </div>
        <div class="dialog-actions">
          <button id="dlg-cancel">取消</button>
        </div>
      </div>`,l("dialog-overlay").style.display="flex",l("dlg-cancel").onclick=()=>l("dialog-overlay").style.display="none",document.querySelectorAll("[data-add-expert]").forEach(c=>{c.addEventListener("click",async()=>{const E=c.dataset.addExpert;try{await N(t.id,E,!0),v=await y(t.id),l("dialog-overlay").style.display="none",b("settings")}catch(w){alert(String(w))}})})},n.querySelectorAll(".toggle-expert").forEach(s=>{s.addEventListener("change",async()=>{const u=s.dataset.expert,m=s.checked;try{await N(t.id,u,m),v=await y(t.id)}catch(c){alert(String(c))}})}),n.querySelectorAll(".btn-remove-expert").forEach(s=>{s.addEventListener("click",async()=>{const u=s.dataset.expert;try{await ot(t.id,u),v=await y(t.id),b("settings")}catch(m){alert(String(m))}})}),document.getElementById("btn-add-skill").onclick=()=>{const s=new Set((t.skills||[]).map(u=>u.id));dt().then(u=>{const m=u.filter(c=>!s.has(c.id));l("dialog-overlay").innerHTML=`
        <div class="dialog wide">
          <h3>添加技能</h3>
          ${m.length===0?"<p>所有技能已添加</p>":""}
          <div style="max-height:300px;overflow-y:auto">
          ${m.map(c=>`
            <div class="expert-card" style="cursor:pointer" data-add-skill="${o(c.id)}">
              <div class="expert-name">🔧 ${o(c.name)}</div>
              <div class="expert-id">${o(c.id)}</div>
              <div class="expert-desc">${o(c.description||"")}</div>
            </div>
          `).join("")}
          </div>
          <div class="dialog-actions">
            <button id="dlg-cancel">取消</button>
          </div>
        </div>`,l("dialog-overlay").style.display="flex",l("dlg-cancel").onclick=()=>l("dialog-overlay").style.display="none",document.querySelectorAll("[data-add-skill]").forEach(c=>{c.addEventListener("click",async()=>{const E=c.dataset.addSkill;try{await O(t.id,E,!0),v=await y(t.id),l("dialog-overlay").style.display="none",b("settings")}catch(w){alert(String(w))}})})}).catch(u=>alert(String(u)))},n.querySelectorAll(".toggle-skill").forEach(s=>{s.addEventListener("change",async()=>{const u=s.dataset.skill,m=s.checked;try{await O(t.id,u,m),v=await y(t.id)}catch(c){alert(String(c))}})}),n.querySelectorAll(".btn-remove-skill").forEach(s=>{s.addEventListener("click",async()=>{const u=s.dataset.skill;try{await ct(t.id,u),v=await y(t.id),b("settings")}catch(m){alert(String(m))}})}),document.getElementById("btn-add-member").onclick=async()=>{const s=l("new-member-id").value.trim(),u=l("new-member-role").value;if(s)try{await q(t.id,s,u),v=await y(t.id),b("settings")}catch(m){alert(String(m))}},n.querySelectorAll(".role-select").forEach(s=>{s.addEventListener("change",async()=>{const u=s.dataset.user,m=s.value;try{await q(t.id,u,m),v=await y(t.id),b("settings")}catch(c){alert(String(c))}})}),n.querySelectorAll(".btn-remove-member").forEach(s=>{s.addEventListener("click",async()=>{const u=s.dataset.user;try{await lt(t.id,u),v=await y(t.id),b("settings")}catch(m){alert(String(m))}})})}function H(t,e){var n;(n=i.stream)==null||n.disconnect(),i.projectId=t,i.taskId=e,i.pendingHumanRequest=null,f(),h(),F()}async function F(){if(!i.projectId||!i.taskId){j();return}I=null,l("main-content").innerHTML=`
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
  `;const t=l("chat-input");t.addEventListener("input",()=>{t.style.height="auto",t.style.height=Math.min(t.scrollHeight,120)+"px"}),t.addEventListener("keydown",e=>{e.key==="Enter"&&!e.shiftKey&&(e.preventDefault(),l("chat-form").requestSubmit())});try{const e=await nt(i.projectId,i.taskId),n=i.tasks.get(i.projectId)||[];n.find(r=>r.taskId===e.taskId)||(n.push(e),i.tasks.set(i.projectId,n),h());const a=document.getElementById("chat-title");a&&(a.textContent=e.title)}catch(e){const n=(e==null?void 0:e.message)||String(e);if(n.includes("VIEWER")||n.includes("FORBIDDEN")||n.includes("403")){i.taskId="",f(),l("main-content").innerHTML=`
        <div class="panel" style="margin-top:60px;text-align:center">
          <h3>权限不足</h3>
          <p>你当前是 VIEWER 角色，只能查看项目，不能进入对话。</p>
          <button id="btn-back-project" class="primary" style="margin-top:12px">返回项目</button>
        </div>
      `,document.getElementById("btn-back-project").onclick=()=>T(i.projectId),h();return}const a=document.getElementById("chat-title");a&&(a.textContent="对话")}kt(),l("chat-form").addEventListener("submit",async e=>{e.preventDefault();const n=t.value.trim();if(n){t.value="",t.style.height="auto";try{await it(i.projectId,i.taskId,n)}catch(a){$t("error",String(a),"system")}}})}function kt(){var n;(n=i.stream)==null||n.disconnect(),i.stream=new ut(i.projectId,i.taskId),i.stream.setLastSequence(0),I=null;const t=document.getElementById("chat-timeline");t&&(t.innerHTML=""),i.stream.onEvent(a=>{xt(a)}),i.stream.connect();const e=document.getElementById("connection-status");e&&(e.className="dot online",e.textContent="已连接")}function $t(t,e,n){const a=document.getElementById("chat-timeline");a&&(a.insertAdjacentHTML("beforeend",`<div class="bubble ${t}">
      <div class="meta">${o(n)} · ${P(Date.now())}</div>
      <div class="text">${o(e)}</div>
    </div>`),a.scrollTop=a.scrollHeight)}let I=null,M="";function xt(t){const e=document.getElementById("chat-timeline");if(!e)return;const n=t.type||"";if((n==="confirm"||n==="coordinatorConfirm")&&(i.pendingHumanRequest={id:t.questionId||"",question:t.content||"Agent 需要你的确认",type:"CLARIFICATION",status:"PENDING"},wt(t)),n==="userMessage"){I=null,M="";const d=t.agentId||"user",p=d===A().user;e.insertAdjacentHTML("beforeend",`<div class="bubble ${p?"user":"other"}">
        <div class="meta">${o(d)} · ${P(t.timestamp)}</div>
        <div class="text">${o(t.content||"")}</div>
      </div>`),e.scrollTop=e.scrollHeight;return}let a="";if(n==="thinkingDelta"||n==="thinking")a=t.text||"";else if(n==="textDelta")a=t.text||"";else if(n==="chat"||n==="coordinatorChat")a=t.content||"";else if(n==="end")(t.content||"").startsWith("{")||(a=t.content||"");else if(n==="coordinatorPhase"||n==="liveStatus"||n==="coordinatorPlanUpdate"||n==="coordinatorNewPlanStep"||n==="coordinatorError"||n==="planUpdate"||n==="newPlanStep"||n==="error"||n==="taskInQueue"){const d=Et({type:n,...t});if(a=t.content||t.text||d||"",!a&&(n==="coordinatorPlanUpdate"||n==="planUpdate")){const p=t.tasks;p!=null&&p.length&&(a="计划: "+p.map(x=>x.title).join(", "))}}if(!a)return;t.agentId&&t.agentId!==M&&(M=t.agentId),I||(I=document.createElement("div"),I.className="bubble system",I.innerHTML=`
      <div class="reply-agent">${o(M)}</div>
      <div class="reply-output"></div>
    `,e.appendChild(I));const r=I.querySelector(".reply-output");n==="thinkingDelta"||n==="thinking"||n==="textDelta"?r.textContent+=a:n==="chat"||n==="coordinatorChat"||n==="end"&&!(t.content||"").startsWith("{")?(r.textContent&&(r.textContent+=`

`),r.textContent+=a):(r.textContent&&(r.textContent+=`
`),r.textContent+="["+P(t.timestamp)+"] "+a),e.scrollTop=e.scrollHeight}function Et(t){const e=t,n=e.type||"",a=e.status||"",r=e.content||e.text||"",d={userMessage:"用户消息",coordinatorPhase:"阶段",coordinatorChat:"回复",coordinatorConfirm:"需要确认",coordinatorError:"错误",coordinatorPlanUpdate:"计划更新",coordinatorNewPlanStep:"任务开始",coordinatorRunCancelled:"已取消",liveStatus:"",thinkingStart:"开始思考",thinkingDelta:"思考中",thinking:"思考",thinkingEnd:"思考结束",textDelta:"输出中",streamStart:"开始输出",streamEnd:"输出结束",chat:"回复",end:"完成",error:"错误",confirm:"需要确认",planUpdate:"计划更新",newPlanStep:"任务开始",taskInQueue:"排队中",toolUsed:"工具调用",toolResult:"工具结果",weblink:"打开链接",file:"文件",directory:"目录",sidebarDisplay:"工作区",clearBoundary:"上下文清理",compactBoundary:"上下文压缩",reconnect:"重连"};return n==="coordinatorPhase"?{analyzing:"正在理解需求",planning:"正在制定计划",dispatching:"正在分配专家",answering:"正在整理回复",waiting_human:"需要确认",completed:"已完成",failed:"执行失败"}[a]||r||"阶段转换":n==="liveStatus"?r||"状态更新":d[n]||r||null}function wt(t){const e=document.getElementById("hitl-panel");if(!e)return;const n=t.content||"Agent 需要你的确认",a=t.questions;e.className="hitl-panel",a!=null&&a.length?(e.innerHTML=a.map(r=>{var d;return`
      <div class="hitl-question">
        <strong>${o(r.header||r.question)}</strong>
        <p>${o(r.question)}</p>
        ${(d=r.options)!=null&&d.length?`<div>${r.options.map(p=>`<label class="hitl-option"><input type="${r.multiSelect?"checkbox":"radio"}" name="hitl-${o(r.header)}" value="${o(p.label)}"> ${o(p.label)} — ${o(p.description)}</label>`).join("")}</div>`:`<input class="hitl-input" data-question="${o(r.question)}" placeholder="输入你的回答...">`}
      </div>`}).join("")+'<button class="primary" id="hitl-submit">提交回答</button>',l("hitl-submit").onclick=async()=>{const r={};e.querySelectorAll(".hitl-input").forEach(d=>{const p=d;r[p.dataset.question||"answer"]=p.value.trim()}),e.querySelectorAll("input:checked").forEach(d=>{r[d.name.replace("hitl-","")]=d.value}),await U(r)}):(e.innerHTML=`
      <strong>${o(n)}</strong>
      <input id="hitl-answer" placeholder="输入回答...">
      <button class="primary" id="hitl-submit">提交</button>
    `,l("hitl-submit").onclick=async()=>{const r=l("hitl-answer").value.trim();r&&await U({answer:r})})}async function U(t){const e=i.pendingHumanRequest;if(e)try{await st(i.projectId,e.id,"ANSWER",t),i.pendingHumanRequest=null;const n=document.getElementById("hitl-panel");n&&(n.className="hitl-panel hidden")}catch(n){alert(String(n))}}document.addEventListener("DOMContentLoaded",pt);
