var _=Object.defineProperty;var X=(t,e,n)=>e in t?_(t,e,{enumerable:!0,configurable:!0,writable:!0,value:n}):t[e]=n;var S=(t,e,n)=>X(t,typeof e!="symbol"?e+"":e,n);(function(){const e=document.createElement("link").relList;if(e&&e.supports&&e.supports("modulepreload"))return;for(const s of document.querySelectorAll('link[rel="modulepreload"]'))i(s);new MutationObserver(s=>{for(const d of s)if(d.type==="childList")for(const l of d.addedNodes)l.tagName==="LINK"&&l.rel==="modulepreload"&&i(l)}).observe(document,{childList:!0,subtree:!0});function n(s){const d={};return s.integrity&&(d.integrity=s.integrity),s.referrerPolicy&&(d.referrerPolicy=s.referrerPolicy),s.crossOrigin==="use-credentials"?d.credentials="include":s.crossOrigin==="anonymous"?d.credentials="omit":d.credentials="same-origin",d}function i(s){if(s.ep)return;s.ep=!0;const d=n(s);fetch(s.href,d)}})();let A=localStorage.getItem("tenant")||"demo-tenant",D=localStorage.getItem("user")||"demo-owner";function F(t,e){A=t,D=e,localStorage.setItem("tenant",t),localStorage.setItem("user",e)}function M(){return{tenant:A,user:D}}function K(){return{"X-Tenant-Id":A,"X-User-Id":D,"Content-Type":"application/json"}}async function g(t,e={}){const n=await fetch(t,{...e,headers:{...K(),...e.headers||{}}});if(!n.ok){const i=await n.json().catch(()=>({message:n.statusText}));throw new Error(i.message||`HTTP ${n.status}`)}return n.status===204?null:n.json()}function Q(){return g("/api/v1/projects")}function b(t){return g(`/api/v1/projects/${t}`)}function z(t,e){return g("/api/v1/projects",{method:"POST",body:JSON.stringify({name:t,description:e||""})})}function G(t,e){return g(`/api/v1/projects/${t}`,{method:"PATCH",body:JSON.stringify(e)})}function Y(t,e){return g(`/api/v1/projects/${t}/tasks`,{method:"POST",body:JSON.stringify({title:e})})}function Z(t,e){return g(`/api/v1/projects/${t}/tasks/${e}`)}function tt(t,e){return g(`/api/v1/projects/${t}/tasks/${e}`,{method:"DELETE"})}function et(t,e,n){const i=crypto.randomUUID();return g(`/api/v1/projects/${t}/tasks/${e}/messages`,{method:"POST",body:JSON.stringify({client_message_id:i,text:n,attachment_refs:[],idempotency_key:i})})}function nt(t,e,n,i){return g(`/api/v1/projects/${t}/human-requests/${e}/responses`,{method:"POST",body:JSON.stringify({decision:n,response:i,idempotencyKey:crypto.randomUUID()})})}function at(){return g("/api/v1/experts")}function $(t){return g(`/api/v1/projects/${t}/tasks`)}function H(t,e,n){return g(`/api/v1/projects/${t}/experts`,{method:"POST",body:JSON.stringify({expertId:e,enabled:n})})}function it(t,e){return g(`/api/v1/projects/${t}/experts/${e}`,{method:"DELETE"})}function C(t,e,n){return g(`/api/v1/projects/${t}/members`,{method:"POST",body:JSON.stringify({userId:e,role:n})})}function st(t,e){return g(`/api/v1/projects/${t}/members/${e}`,{method:"DELETE"})}class ot{constructor(e,n){S(this,"abort",null);S(this,"lastSequence",0);S(this,"listeners",[]);this.projectId=e,this.taskId=n}onEvent(e){this.listeners.push(e)}setLastSequence(e){this.lastSequence=e}async connect(){var s;(s=this.abort)==null||s.abort();const e=new AbortController;this.abort=e;const{tenant:n,user:i}=M();for(;;)try{const d=await fetch(`/api/v1/projects/${this.projectId}/tasks/${this.taskId}/events`,{headers:{"X-Tenant-Id":n,"X-User-Id":i,"Last-Event-ID":String(this.lastSequence)},signal:e.signal});if(!d.ok)throw new Error(`SSE connect failed: ${d.status}`);const l=d.body.getReader(),k=new TextDecoder;let o="";for(;;){const{done:u,value:m}=await l.read();if(u)break;o+=k.decode(m,{stream:!0});let p;for(;(p=o.indexOf(`

`))>=0;){const L=o.slice(0,p);o=o.slice(p+2);const j=L.split(`
`).filter(E=>E.startsWith("data:")).map(E=>E.slice(5).trim()).join("");if(j)try{const E=JSON.parse(j);this.lastSequence=Math.max(this.lastSequence,E.sequence||0);for(const V of this.listeners)try{V(E)}catch{}}catch{}}}await q(1e3)}catch(d){if(d instanceof DOMException&&d.name==="AbortError")return;await q(2e3)}}disconnect(){var e;(e=this.abort)==null||e.abort()}}function q(t){return new Promise(e=>setTimeout(e,t))}const a={projectId:localStorage.getItem("projectId")||"",taskId:localStorage.getItem("taskId")||"",stream:null,projects:[],tasks:new Map,pendingHumanRequest:null};function y(){localStorage.setItem("projectId",a.projectId),localStorage.setItem("taskId",a.taskId)}const c=t=>document.getElementById(t),r=t=>String(t??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");function P(t){return t?new Date(t).toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"}):""}async function rt(){const t=document.getElementById("app");t.innerHTML=`
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
  `,ct(),U();try{if(a.projects=await Q(),a.projectId)try{const e=await $(a.projectId);a.tasks.set(a.projectId,e)}catch{}}catch{}v(),a.projectId&&a.taskId?B(a.projectId,a.taskId):a.projectId?x(a.projectId):w()}function ct(){O(),R()}function O(){const t=document.getElementById("sidebar-tree"),e=[];for(const n of a.projects){a.projectId,n.id;const i=a.tasks.get(n.id)||[],s=a.projectId===n.id;e.push(`
      <div class="tree-folder ${s?"active":""}">
        <div class="tree-row project-row"
             data-action="select-project"
             data-project="${r(n.id)}">
          <span class="tree-arrow" data-action="toggle-expand" data-project="${r(n.id)}">${s?"▼":"▶"}</span>
          <span class="tree-icon">📁</span>
          <span class="tree-label">${r(n.name)}</span>
        </div>
        ${s?dt(n.id,i):""}
      </div>
    `)}e.length===0&&e.push('<div class="tree-empty">暂无项目<br><small>点击下方按钮创建</small></div>'),t.innerHTML=e.join(""),lt()}function dt(t,e){return e.length===0?'<div class="tree-empty-sub">暂无任务</div>':e.map(n=>{const i=a.taskId===n.taskId;return`
      <div class="tree-row task-row ${i?"active":""}"
           data-action="select-task"
           data-project="${r(t)}"
           data-task="${r(n.taskId)}">
        <span class="tree-icon">💬</span>
        <span class="tree-label">${r(n.title)}</span>
        ${i?'<span class="tree-badge">●</span>':""}
        <button class="tree-del" data-action="delete-task" data-project="${r(t)}" data-task="${r(n.taskId)}" title="删除">×</button>
      </div>`}).join("")}function R(){const t=document.getElementById("sidebar-actions");t.innerHTML=`
    <button id="btn-new-project" class="sidebar-btn">+ 新建项目</button>
    <button id="btn-add-project" class="sidebar-btn">🔗 加入已有项目</button>
    <button id="btn-new-task" class="sidebar-btn" ${a.projectId?"":"disabled"}>+ 新建任务</button>
  `,document.getElementById("btn-new-project").onclick=pt,document.getElementById("btn-add-project").onclick=mt,document.getElementById("btn-new-task").onclick=()=>{a.projectId&&W()}}function U(){const{tenant:t,user:e}=M(),n=document.getElementById("sidebar-identity");n.innerHTML=`
    <div class="identity-row">
      <span class="identity-label">${r(t)} / ${r(e)}</span>
      <button class="identity-btn" id="identity-edit-btn">⚙</button>
    </div>
  `,document.getElementById("identity-edit-btn").onclick=ut}function lt(){document.querySelectorAll(".tree-row").forEach(t=>{t.addEventListener("click",async e=>{const n=e.target,i=n.dataset.action||t.dataset.action,s=t.dataset.project;if(i==="delete-task"){e.stopPropagation();const d=n.dataset.task;confirm("确定删除这个任务吗？")&&tt(s,d).then(async()=>{a.taskId===d&&(a.taskId="",y(),w());try{const l=await $(s);a.tasks.set(s,l)}catch{}v()}).catch(l=>alert(String(l)));return}if(i==="toggle-expand"){if(e.stopPropagation(),a.projectId===s)a.projectId="",a.taskId="",y(),v(),w();else{a.projectId=s,y();try{const d=await $(s);a.tasks.set(s,d)}catch{}v()}return}if(i==="select-project")x(s);else if(i==="select-task"){const d=t.dataset.task;B(s,d)}})})}function v(){O(),R()}function ut(){const{tenant:t,user:e}=M();c("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>身份设置</h3>
      <label>Tenant ID <input id="dlg-tenant" value="${r(t)}"></label>
      <label>User ID <input id="dlg-user" value="${r(e)}"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-identity-save">保存</button>
        <button id="dlg-identity-cancel">取消</button>
      </div>
    </div>
  `,c("dialog-overlay").style.display="flex",c("dlg-identity-cancel").onclick=()=>c("dialog-overlay").style.display="none",c("dlg-identity-save").onclick=()=>{F(c("dlg-tenant").value.trim(),c("dlg-user").value.trim()),c("dialog-overlay").style.display="none",U()}}function pt(){c("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建项目</h3>
      <label>名称 <input id="dlg-name" autofocus></label>
      <label>描述 <input id="dlg-desc"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,c("dialog-overlay").style.display="flex",c("dlg-cancel").onclick=()=>c("dialog-overlay").style.display="none",c("dlg-create").onclick=async()=>{const t=c("dlg-name").value.trim();if(t)try{const e=await z(t,c("dlg-desc").value.trim());a.projects.unshift(e),a.projectId=e.id,a.taskId="",y(),c("dialog-overlay").style.display="none",v(),x(e.id)}catch(e){alert(String(e))}}}function mt(){c("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>加入已有项目</h3>
      <label>项目 ID <input id="dlg-project-id" autofocus placeholder="粘贴项目 UUID"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-add">加入</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,c("dialog-overlay").style.display="flex",c("dlg-cancel").onclick=()=>c("dialog-overlay").style.display="none",c("dlg-add").onclick=async()=>{const t=c("dlg-project-id").value.trim();if(t)try{const e=await b(t);a.projects.find(n=>n.id===t)||a.projects.unshift(e),a.projectId=t,a.taskId="",y(),c("dialog-overlay").style.display="none",v(),x(t)}catch(e){alert(String(e))}}}function W(){c("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建任务</h3>
      <label>标题 <input id="dlg-title" value="新对话" autofocus></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,c("dialog-overlay").style.display="flex",c("dlg-cancel").onclick=()=>c("dialog-overlay").style.display="none",c("dlg-create").onclick=async()=>{const t=c("dlg-title").value.trim()||"新对话";try{const e=await Y(a.projectId,t);a.taskId=e.taskId,y(),c("dialog-overlay").style.display="none";try{const n=await $(a.projectId);a.tasks.set(a.projectId,n)}catch{}v(),J()}catch(e){alert(String(e))}}}function w(){c("main-content").innerHTML=`
    <div class="panel welcome-panel">
      <h2>TeamCoordinator</h2>
      <p>AI Agent 编排服务测试前端</p>
      <p class="hint">从左侧创建或选择一个项目开始</p>
    </div>
  `}let h=null;async function x(t){a.projectId=t,y(),v();try{h=await b(t);const e=a.projects.findIndex(i=>i.id===t);e>=0?a.projects[e]=h:a.projects.push(h);const n=await $(t);a.tasks.set(t,n),v(),I("overview")}catch(e){c("main-content").innerHTML=`
      <div class="panel error">
        <p>项目加载失败: ${r(e)}</p>
        <button id="btn-remove-project">从列表中移除</button>
      </div>
    `,document.getElementById("btn-remove-project").onclick=()=>{a.projects=a.projects.filter(n=>n.id!==t),a.projectId="",a.taskId="",y(),v(),w()}}}async function I(t){const e=h;e&&(c("main-content").innerHTML=`
    <div class="panel">
      <div class="tabs">
        <button class="tab ${t==="overview"?"active":""}" id="tab-overview">概述</button>
        <button class="tab ${t==="settings"?"active":""}" id="tab-settings">配置</button>
      </div>
      <div id="tab-content"></div>
    </div>
  `,document.getElementById("tab-overview").onclick=()=>I("overview"),document.getElementById("tab-settings").onclick=()=>I("settings"),t==="overview"?gt(e):await vt(e))}function gt(t){const e=document.getElementById("tab-content");e.innerHTML=`
    <h2>${r(t.name)} <span class="badge">${r(t.status)}</span></h2>
    <p>${r(t.description||"无描述")}</p>
    <h3>成员 (${t.members.length})</h3>
    <ul class="detail-list">
      ${t.members.map(n=>`<li>${r(n.userId)} <span class="role-tag">${r(n.role)}</span></li>`).join("")}
    </ul>
    <h3>专家 (${t.experts.length})</h3>
    <ul class="detail-list">
      ${t.experts.map(n=>`<li>${r(n.expertId)} ${n.enabled?"✅":"❌"}</li>`).join("")}
    </ul>
    <div class="actions">
      <button class="primary" id="btn-goto-chat">进入对话</button>
    </div>
  `,document.getElementById("btn-goto-chat").onclick=()=>{a.taskId?B(t.id,a.taskId):W()}}async function vt(t){var l,k;let e=[];try{e=await at()}catch{}const n=document.getElementById("tab-content");new Set(t.experts.filter(o=>o.enabled).map(o=>o.expertId));const i=t.coordinatorAgentId||"",s=t.experts.filter(o=>o.expertId!==i);e.filter(o=>o.id!==i);const d=t.experts.some(o=>o.expertId===i&&i);n.innerHTML=`
    <h2>项目配置</h2>

    <h3>基本信息</h3>
    <div class="form-row">
      <label>名称 <input id="cfg-name" value="${r(t.name)}"></label>
      <label>描述 <input id="cfg-desc" value="${r(t.description||"")}"></label>
      <label>主 Agent (Coordinator)
        <select id="cfg-coordinator">
          <option value="">使用全局默认</option>
          ${e.map(o=>`<option value="${r(o.id)}" ${o.id===i?"selected":""}>${r(o.name)} (${r(o.id)})</option>`).join("")}
        </select>
      </label>
      <button class="primary" id="btn-save-info">保存</button>
    </div>

    ${i?`
    <h3>主 Agent</h3>
    <div class="expert-card coordinator-card">
      <div class="expert-name">⭐ ${r(((l=e.find(o=>o.id===i))==null?void 0:l.name)||i)}</div>
      <div class="expert-id">${r(i)}</div>
      <div class="expert-desc">${r(((k=e.find(o=>o.id===i))==null?void 0:k.description)||"")}</div>
    </div>
    `:""}

    <h3>专家团队 ${d?'<span class="hint" style="color:var(--danger)">⚠ 主Agent不能同时在专家团队中，请先移除以避免保存失败</span>':""}</h3>
    ${s.length===0?'<p class="hint">暂无专家</p>':""}
    <div class="expert-grid">
      ${s.map(o=>{const u=e.find(m=>m.id===o.expertId);return`
        <div class="expert-card added">
          <div class="expert-name">${r((u==null?void 0:u.name)||o.expertId)}</div>
          <div class="expert-id">${r(o.expertId)}</div>
          <div class="expert-desc">${r((u==null?void 0:u.description)||"")}</div>
          <div class="expert-actions">
            <label><input type="checkbox" class="toggle-expert" data-expert="${r(o.expertId)}" ${o.enabled?"checked":""}> 启用</label>
            <button class="small danger btn-remove-expert" data-expert="${r(o.expertId)}">移除</button>
          </div>
        </div>`}).join("")}
    </div>
    <button class="primary" id="btn-add-expert">+ 添加专家</button>

    <h3>成员</h3>
    <div id="member-list">
      ${t.members.map(o=>`
        <div class="member-row">
          <span>${r(o.userId)}</span>
          ${o.role!=="OWNER"?`<select class="role-select" data-user="${r(o.userId)}">
                <option value="ADMIN" ${o.role==="ADMIN"?"selected":""}>ADMIN</option>
                <option value="MEMBER" ${o.role==="MEMBER"?"selected":""}>MEMBER</option>
                <option value="VIEWER" ${o.role==="VIEWER"?"selected":""}>VIEWER</option>
              </select>
              <button class="small danger btn-remove-member" data-user="${r(o.userId)}">移除</button>`:'<span class="role-tag">OWNER</span>'}
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
  `,document.getElementById("btn-save-info").onclick=async()=>{const o=c("cfg-name").value.trim(),u=c("cfg-desc").value.trim(),m=c("cfg-coordinator").value.trim();if(o)try{await G(t.id,{name:o,description:u,coordinatorAgentId:m}),h=await b(t.id),I("settings")}catch(p){alert(String(p))}},document.getElementById("btn-add-expert").onclick=()=>{const o=new Set(t.experts.map(p=>p.expertId)),u=t.coordinatorAgentId||"",m=e.filter(p=>!o.has(p.id)&&p.id!==u);c("dialog-overlay").innerHTML=`
      <div class="dialog wide">
        <h3>添加专家</h3>
        ${m.length===0?"<p>所有专家已添加</p>":""}
        <div style="max-height:300px;overflow-y:auto">
        ${m.map(p=>`
          <div class="expert-card" style="cursor:pointer" data-add-expert="${r(p.id)}">
            <div class="expert-name">${r(p.name)}</div>
            <div class="expert-id">${r(p.id)}</div>
            <div class="expert-desc">${r(p.description)}</div>
          </div>
        `).join("")}
        </div>
        <div class="dialog-actions">
          <button id="dlg-cancel">取消</button>
        </div>
      </div>`,c("dialog-overlay").style.display="flex",c("dlg-cancel").onclick=()=>c("dialog-overlay").style.display="none",document.querySelectorAll("[data-add-expert]").forEach(p=>{p.addEventListener("click",async()=>{const L=p.dataset.addExpert;try{await H(t.id,L,!0),h=await b(t.id),c("dialog-overlay").style.display="none",I("settings")}catch(j){alert(String(j))}})})},n.querySelectorAll(".toggle-expert").forEach(o=>{o.addEventListener("change",async()=>{const u=o.dataset.expert,m=o.checked;try{await H(t.id,u,m),h=await b(t.id)}catch(p){alert(String(p))}})}),n.querySelectorAll(".btn-remove-expert").forEach(o=>{o.addEventListener("click",async()=>{const u=o.dataset.expert;try{await it(t.id,u),h=await b(t.id),I("settings")}catch(m){alert(String(m))}})}),document.getElementById("btn-add-member").onclick=async()=>{const o=c("new-member-id").value.trim(),u=c("new-member-role").value;if(o)try{await C(t.id,o,u),h=await b(t.id),I("settings")}catch(m){alert(String(m))}},n.querySelectorAll(".role-select").forEach(o=>{o.addEventListener("change",async()=>{const u=o.dataset.user,m=o.value;try{await C(t.id,u,m),h=await b(t.id),I("settings")}catch(p){alert(String(p))}})}),n.querySelectorAll(".btn-remove-member").forEach(o=>{o.addEventListener("click",async()=>{const u=o.dataset.user;try{await st(t.id,u),h=await b(t.id),I("settings")}catch(m){alert(String(m))}})})}function B(t,e){var n;(n=a.stream)==null||n.disconnect(),a.projectId=t,a.taskId=e,a.pendingHumanRequest=null,y(),v(),J()}async function J(){if(!a.projectId||!a.taskId){w();return}f=null,c("main-content").innerHTML=`
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
  `;const t=c("chat-input");t.addEventListener("input",()=>{t.style.height="auto",t.style.height=Math.min(t.scrollHeight,120)+"px"}),t.addEventListener("keydown",e=>{e.key==="Enter"&&!e.shiftKey&&(e.preventDefault(),c("chat-form").requestSubmit())});try{const e=await Z(a.projectId,a.taskId),n=a.tasks.get(a.projectId)||[];n.find(s=>s.taskId===e.taskId)||(n.push(e),a.tasks.set(a.projectId,n),v());const i=document.getElementById("chat-title");i&&(i.textContent=e.title)}catch(e){const n=(e==null?void 0:e.message)||String(e);if(n.includes("VIEWER")||n.includes("FORBIDDEN")||n.includes("403")){a.taskId="",y(),c("main-content").innerHTML=`
        <div class="panel" style="margin-top:60px;text-align:center">
          <h3>权限不足</h3>
          <p>你当前是 VIEWER 角色，只能查看项目，不能进入对话。</p>
          <button id="btn-back-project" class="primary" style="margin-top:12px">返回项目</button>
        </div>
      `,document.getElementById("btn-back-project").onclick=()=>x(a.projectId),v();return}const i=document.getElementById("chat-title");i&&(i.textContent="对话")}ht(),c("chat-form").addEventListener("submit",async e=>{e.preventDefault();const n=t.value.trim();if(n){t.value="",t.style.height="auto";try{await et(a.projectId,a.taskId,n)}catch(i){yt("error",String(i),"system")}}})}function ht(){var n;(n=a.stream)==null||n.disconnect(),a.stream=new ot(a.projectId,a.taskId),a.stream.setLastSequence(0),f=null;const t=document.getElementById("chat-timeline");t&&(t.innerHTML=""),a.stream.onEvent(i=>{bt(i)}),a.stream.connect();const e=document.getElementById("connection-status");e&&(e.className="dot online",e.textContent="已连接")}function yt(t,e,n){const i=document.getElementById("chat-timeline");i&&(i.insertAdjacentHTML("beforeend",`<div class="bubble ${t}">
      <div class="meta">${r(n)} · ${P(Date.now())}</div>
      <div class="text">${r(e)}</div>
    </div>`),i.scrollTop=i.scrollHeight)}let f=null,T="";function bt(t){const e=document.getElementById("chat-timeline");if(!e)return;const n=t.type||"";if((n==="confirm"||n==="coordinatorConfirm")&&(a.pendingHumanRequest={id:t.questionId||"",question:t.content||"Agent 需要你的确认",type:"CLARIFICATION",status:"PENDING"},It(t)),n==="userMessage"){f=null,T="";const d=t.agentId||"user",l=d===M().user;e.insertAdjacentHTML("beforeend",`<div class="bubble ${l?"user":"other"}">
        <div class="meta">${r(d)} · ${P(t.timestamp)}</div>
        <div class="text">${r(t.content||"")}</div>
      </div>`),e.scrollTop=e.scrollHeight;return}let i="";if(n==="thinkingDelta"||n==="thinking")i=t.text||"";else if(n==="textDelta")i=t.text||"";else if(n==="chat"||n==="coordinatorChat")i=t.content||"";else if(n==="end")(t.content||"").startsWith("{")||(i=t.content||"");else if(n==="coordinatorPhase"||n==="liveStatus"||n==="coordinatorPlanUpdate"||n==="coordinatorNewPlanStep"||n==="coordinatorError"||n==="planUpdate"||n==="newPlanStep"||n==="error"||n==="taskInQueue"){const d=ft({type:n,...t});if(i=t.content||t.text||d||"",!i&&(n==="coordinatorPlanUpdate"||n==="planUpdate")){const l=t.tasks;l!=null&&l.length&&(i="计划: "+l.map(k=>k.title).join(", "))}}if(!i)return;t.agentId&&t.agentId!==T&&(T=t.agentId),f||(f=document.createElement("div"),f.className="bubble system",f.innerHTML=`
      <div class="reply-agent">${r(T)}</div>
      <div class="reply-output"></div>
    `,e.appendChild(f));const s=f.querySelector(".reply-output");n==="thinkingDelta"||n==="thinking"||n==="textDelta"?s.textContent+=i:n==="chat"||n==="coordinatorChat"||n==="end"&&!(t.content||"").startsWith("{")?(s.textContent&&(s.textContent+=`

`),s.textContent+=i):(s.textContent&&(s.textContent+=`
`),s.textContent+="["+P(t.timestamp)+"] "+i),e.scrollTop=e.scrollHeight}function ft(t){const e=t,n=e.type||"",i=e.status||"",s=e.content||e.text||"",d={userMessage:"用户消息",coordinatorPhase:"阶段",coordinatorChat:"回复",coordinatorConfirm:"需要确认",coordinatorError:"错误",coordinatorPlanUpdate:"计划更新",coordinatorNewPlanStep:"任务开始",coordinatorRunCancelled:"已取消",liveStatus:"",thinkingStart:"开始思考",thinkingDelta:"思考中",thinking:"思考",thinkingEnd:"思考结束",textDelta:"输出中",streamStart:"开始输出",streamEnd:"输出结束",chat:"回复",end:"完成",error:"错误",confirm:"需要确认",planUpdate:"计划更新",newPlanStep:"任务开始",taskInQueue:"排队中",toolUsed:"工具调用",toolResult:"工具结果",weblink:"打开链接",file:"文件",directory:"目录",sidebarDisplay:"工作区",clearBoundary:"上下文清理",compactBoundary:"上下文压缩",reconnect:"重连"};return n==="coordinatorPhase"?{analyzing:"正在理解需求",planning:"正在制定计划",dispatching:"正在分配专家",answering:"正在整理回复",waiting_human:"需要确认",completed:"已完成",failed:"执行失败"}[i]||s||"阶段转换":n==="liveStatus"?s||"状态更新":d[n]||s||null}function It(t){const e=document.getElementById("hitl-panel");if(!e)return;const n=t.content||"Agent 需要你的确认",i=t.questions;e.className="hitl-panel",i!=null&&i.length?(e.innerHTML=i.map(s=>{var d;return`
      <div class="hitl-question">
        <strong>${r(s.header||s.question)}</strong>
        <p>${r(s.question)}</p>
        ${(d=s.options)!=null&&d.length?`<div>${s.options.map(l=>`<label class="hitl-option"><input type="${s.multiSelect?"checkbox":"radio"}" name="hitl-${r(s.header)}" value="${r(l.label)}"> ${r(l.label)} — ${r(l.description)}</label>`).join("")}</div>`:`<input class="hitl-input" data-question="${r(s.question)}" placeholder="输入你的回答...">`}
      </div>`}).join("")+'<button class="primary" id="hitl-submit">提交回答</button>',c("hitl-submit").onclick=async()=>{const s={};e.querySelectorAll(".hitl-input").forEach(d=>{const l=d;s[l.dataset.question||"answer"]=l.value.trim()}),e.querySelectorAll("input:checked").forEach(d=>{s[d.name.replace("hitl-","")]=d.value}),await N(s)}):(e.innerHTML=`
      <strong>${r(n)}</strong>
      <input id="hitl-answer" placeholder="输入回答...">
      <button class="primary" id="hitl-submit">提交</button>
    `,c("hitl-submit").onclick=async()=>{const s=c("hitl-answer").value.trim();s&&await N({answer:s})})}async function N(t){const e=a.pendingHumanRequest;if(e)try{await nt(a.projectId,e.id,"ANSWER",t),a.pendingHumanRequest=null;const n=document.getElementById("hitl-panel");n&&(n.className="hitl-panel hidden")}catch(n){alert(String(n))}}document.addEventListener("DOMContentLoaded",rt);
