var _=Object.defineProperty;var X=(t,e,n)=>e in t?_(t,e,{enumerable:!0,configurable:!0,writable:!0,value:n}):t[e]=n;var w=(t,e,n)=>X(t,typeof e!="symbol"?e+"":e,n);(function(){const e=document.createElement("link").relList;if(e&&e.supports&&e.supports("modulepreload"))return;for(const i of document.querySelectorAll('link[rel="modulepreload"]'))a(i);new MutationObserver(i=>{for(const r of i)if(r.type==="childList")for(const l of r.addedNodes)l.tagName==="LINK"&&l.rel==="modulepreload"&&a(l)}).observe(document,{childList:!0,subtree:!0});function n(i){const r={};return i.integrity&&(r.integrity=i.integrity),i.referrerPolicy&&(r.referrerPolicy=i.referrerPolicy),i.crossOrigin==="use-credentials"?r.credentials="include":i.crossOrigin==="anonymous"?r.credentials="omit":r.credentials="same-origin",r}function a(i){if(i.ep)return;i.ep=!0;const r=n(i);fetch(i.href,r)}})();let T=localStorage.getItem("tenant")||"demo-tenant",M=localStorage.getItem("user")||"demo-owner";function F(t,e){T=t,M=e,localStorage.setItem("tenant",t),localStorage.setItem("user",e)}function $(){return{tenant:T,user:M}}function K(){return{"X-Tenant-Id":T,"X-User-Id":M,"Content-Type":"application/json"}}async function d(t,e={}){const n=await fetch(t,{...e,headers:{...K(),...e.headers||{}}});if(!n.ok){const a=await n.json().catch(()=>({message:n.statusText}));throw new Error(a.message||`HTTP ${n.status}`)}return n.status===204?null:n.json()}function Q(){return d("/api/v1/projects")}function g(t){return d(`/api/v1/projects/${t}`)}function z(t,e){return d("/api/v1/projects",{method:"POST",body:JSON.stringify({name:t,description:e||""})})}function G(t,e){return d(`/api/v1/projects/${t}`,{method:"PATCH",body:JSON.stringify(e)})}function Y(t,e){return d(`/api/v1/projects/${t}/tasks`,{method:"POST",body:JSON.stringify({title:e})})}function Z(t,e){return d(`/api/v1/projects/${t}/tasks/${e}`)}function tt(t,e){return d(`/api/v1/projects/${t}/tasks/${e}`,{method:"DELETE"})}function et(t,e,n){const a=crypto.randomUUID();return d(`/api/v1/projects/${t}/tasks/${e}/messages`,{method:"POST",body:JSON.stringify({client_message_id:a,text:n,attachment_refs:[],idempotency_key:a})})}function nt(t,e,n,a){return d(`/api/v1/projects/${t}/human-requests/${e}/responses`,{method:"POST",body:JSON.stringify({decision:n,response:a,idempotencyKey:crypto.randomUUID()})})}function at(){return d("/api/v1/experts")}function I(t){return d(`/api/v1/projects/${t}/tasks`)}function D(t,e,n){return d(`/api/v1/projects/${t}/experts`,{method:"POST",body:JSON.stringify({expertId:e,enabled:n})})}function it(t,e){return d(`/api/v1/projects/${t}/experts/${e}`,{method:"DELETE"})}function B(t,e,n){return d(`/api/v1/projects/${t}/members`,{method:"POST",body:JSON.stringify({userId:e,role:n})})}function st(t,e){return d(`/api/v1/projects/${t}/members/${e}`,{method:"DELETE"})}class rt{constructor(e,n){w(this,"abort",null);w(this,"lastSequence",0);w(this,"listeners",[]);this.projectId=e,this.taskId=n}onEvent(e){this.listeners.push(e)}setLastSequence(e){this.lastSequence=e}async connect(){var i;(i=this.abort)==null||i.abort();const e=new AbortController;this.abort=e;const{tenant:n,user:a}=$();for(;;)try{const r=await fetch(`/api/v1/projects/${this.projectId}/tasks/${this.taskId}/events`,{headers:{"X-Tenant-Id":n,"X-User-Id":a,"Last-Event-ID":String(this.lastSequence)},signal:e.signal});if(!r.ok)throw new Error(`SSE connect failed: ${r.status}`);const l=r.body.getReader(),b=new TextDecoder;let f="";for(;;){const{done:U,value:W}=await l.read();if(U)break;f+=b.decode(W,{stream:!0});let j;for(;(j=f.indexOf(`

`))>=0;){const J=f.slice(0,j);f=f.slice(j+2);const P=J.split(`
`).filter(v=>v.startsWith("data:")).map(v=>v.slice(5).trim()).join("");if(P)try{const v=JSON.parse(P);this.lastSequence=Math.max(this.lastSequence,v.sequence||0);for(const V of this.listeners)try{V(v)}catch{}}catch{}}}await H(1e3)}catch(r){if(r instanceof DOMException&&r.name==="AbortError")return;await H(2e3)}}disconnect(){var e;(e=this.abort)==null||e.abort()}}function H(t){return new Promise(e=>setTimeout(e,t))}const s={projectId:localStorage.getItem("projectId")||"",taskId:localStorage.getItem("taskId")||"",stream:null,projects:[],tasks:new Map,pendingHumanRequest:null};function m(){localStorage.setItem("projectId",s.projectId),localStorage.setItem("taskId",s.taskId)}const o=t=>document.getElementById(t),c=t=>String(t??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");function S(t){return t?new Date(t).toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"}):""}async function ot(){const t=document.getElementById("app");t.innerHTML=`
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
  `,ct(),N();try{if(s.projects=await Q(),s.projectId)try{const e=await I(s.projectId);s.tasks.set(s.projectId,e)}catch{}}catch{}u(),s.projectId&&s.taskId?L(s.projectId,s.taskId):s.projectId?E(s.projectId):k()}function ct(){C(),q()}function C(){const t=document.getElementById("sidebar-tree"),e=[];for(const n of s.projects){s.projectId,n.id;const a=s.tasks.get(n.id)||[],i=s.projectId===n.id;e.push(`
      <div class="tree-folder ${i?"active":""}">
        <div class="tree-row project-row"
             data-action="select-project"
             data-project="${c(n.id)}">
          <span class="tree-arrow" data-action="toggle-expand" data-project="${c(n.id)}">${i?"▼":"▶"}</span>
          <span class="tree-icon">📁</span>
          <span class="tree-label">${c(n.name)}</span>
        </div>
        ${i?lt(n.id,a):""}
      </div>
    `)}e.length===0&&e.push('<div class="tree-empty">暂无项目<br><small>点击下方按钮创建</small></div>'),t.innerHTML=e.join(""),dt()}function lt(t,e){return e.length===0?'<div class="tree-empty-sub">暂无任务</div>':e.map(n=>{const a=s.taskId===n.taskId;return`
      <div class="tree-row task-row ${a?"active":""}"
           data-action="select-task"
           data-project="${c(t)}"
           data-task="${c(n.taskId)}">
        <span class="tree-icon">💬</span>
        <span class="tree-label">${c(n.title)}</span>
        ${a?'<span class="tree-badge">●</span>':""}
        <button class="tree-del" data-action="delete-task" data-project="${c(t)}" data-task="${c(n.taskId)}" title="删除">×</button>
      </div>`}).join("")}function q(){const t=document.getElementById("sidebar-actions");t.innerHTML=`
    <button id="btn-new-project" class="sidebar-btn">+ 新建项目</button>
    <button id="btn-add-project" class="sidebar-btn">🔗 加入已有项目</button>
    <button id="btn-new-task" class="sidebar-btn" ${s.projectId?"":"disabled"}>+ 新建任务</button>
  `,document.getElementById("btn-new-project").onclick=pt,document.getElementById("btn-add-project").onclick=mt,document.getElementById("btn-new-task").onclick=()=>{s.projectId&&O()}}function N(){const{tenant:t,user:e}=$(),n=document.getElementById("sidebar-identity");n.innerHTML=`
    <div class="identity-row">
      <span class="identity-label">${c(t)} / ${c(e)}</span>
      <button class="identity-btn" id="identity-edit-btn">⚙</button>
    </div>
  `,document.getElementById("identity-edit-btn").onclick=ut}function dt(){document.querySelectorAll(".tree-row").forEach(t=>{t.addEventListener("click",async e=>{const n=e.target,a=n.dataset.action||t.dataset.action,i=t.dataset.project;if(a==="delete-task"){e.stopPropagation();const r=n.dataset.task;confirm("确定删除这个任务吗？")&&tt(i,r).then(async()=>{s.taskId===r&&(s.taskId="",m(),k());try{const l=await I(i);s.tasks.set(i,l)}catch{}u()}).catch(l=>alert(String(l)));return}if(a==="toggle-expand"){if(e.stopPropagation(),s.projectId===i)s.projectId="",s.taskId="",m(),u(),k();else{s.projectId=i,m();try{const r=await I(i);s.tasks.set(i,r)}catch{}u()}return}if(a==="select-project")E(i);else if(a==="select-task"){const r=t.dataset.task;L(i,r)}})})}function u(){C(),q()}function ut(){const{tenant:t,user:e}=$();o("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>身份设置</h3>
      <label>Tenant ID <input id="dlg-tenant" value="${c(t)}"></label>
      <label>User ID <input id="dlg-user" value="${c(e)}"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-identity-save">保存</button>
        <button id="dlg-identity-cancel">取消</button>
      </div>
    </div>
  `,o("dialog-overlay").style.display="flex",o("dlg-identity-cancel").onclick=()=>o("dialog-overlay").style.display="none",o("dlg-identity-save").onclick=()=>{F(o("dlg-tenant").value.trim(),o("dlg-user").value.trim()),o("dialog-overlay").style.display="none",N()}}function pt(){o("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建项目</h3>
      <label>名称 <input id="dlg-name" autofocus></label>
      <label>描述 <input id="dlg-desc"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,o("dialog-overlay").style.display="flex",o("dlg-cancel").onclick=()=>o("dialog-overlay").style.display="none",o("dlg-create").onclick=async()=>{const t=o("dlg-name").value.trim();if(t)try{const e=await z(t,o("dlg-desc").value.trim());s.projects.unshift(e),s.projectId=e.id,s.taskId="",m(),o("dialog-overlay").style.display="none",u(),E(e.id)}catch(e){alert(String(e))}}}function mt(){o("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>加入已有项目</h3>
      <label>项目 ID <input id="dlg-project-id" autofocus placeholder="粘贴项目 UUID"></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-add">加入</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,o("dialog-overlay").style.display="flex",o("dlg-cancel").onclick=()=>o("dialog-overlay").style.display="none",o("dlg-add").onclick=async()=>{const t=o("dlg-project-id").value.trim();if(t)try{const e=await g(t);s.projects.find(n=>n.id===t)||s.projects.unshift(e),s.projectId=t,s.taskId="",m(),o("dialog-overlay").style.display="none",u(),E(t)}catch(e){alert(String(e))}}}function O(){o("dialog-overlay").innerHTML=`
    <div class="dialog wide">
      <h3>新建任务</h3>
      <label>标题 <input id="dlg-title" value="新对话" autofocus></label>
      <div class="dialog-actions">
        <button class="primary" id="dlg-create">创建</button>
        <button id="dlg-cancel">取消</button>
      </div>
    </div>
  `,o("dialog-overlay").style.display="flex",o("dlg-cancel").onclick=()=>o("dialog-overlay").style.display="none",o("dlg-create").onclick=async()=>{const t=o("dlg-title").value.trim()||"新对话";try{const e=await Y(s.projectId,t);s.taskId=e.taskId,m(),o("dialog-overlay").style.display="none";try{const n=await I(s.projectId);s.tasks.set(s.projectId,n)}catch{}u(),R()}catch(e){alert(String(e))}}}function k(){o("main-content").innerHTML=`
    <div class="panel welcome-panel">
      <h2>TeamCoordinator</h2>
      <p>AI Agent 编排服务测试前端</p>
      <p class="hint">从左侧创建或选择一个项目开始</p>
    </div>
  `}let p=null;async function E(t){s.projectId=t,m(),u();try{p=await g(t);const e=s.projects.findIndex(a=>a.id===t);e>=0?s.projects[e]=p:s.projects.push(p);const n=await I(t);s.tasks.set(t,n),u(),h("overview")}catch(e){o("main-content").innerHTML=`
      <div class="panel error">
        <p>项目加载失败: ${c(e)}</p>
        <button id="btn-remove-project">从列表中移除</button>
      </div>
    `,document.getElementById("btn-remove-project").onclick=()=>{s.projects=s.projects.filter(n=>n.id!==t),s.projectId="",s.taskId="",m(),u(),k()}}}async function h(t){const e=p;e&&(o("main-content").innerHTML=`
    <div class="panel">
      <div class="tabs">
        <button class="tab ${t==="overview"?"active":""}" id="tab-overview">概述</button>
        <button class="tab ${t==="settings"?"active":""}" id="tab-settings">配置</button>
      </div>
      <div id="tab-content"></div>
    </div>
  `,document.getElementById("tab-overview").onclick=()=>h("overview"),document.getElementById("tab-settings").onclick=()=>h("settings"),t==="overview"?gt(e):await yt(e))}function gt(t){const e=document.getElementById("tab-content");e.innerHTML=`
    <h2>${c(t.name)} <span class="badge">${c(t.status)}</span></h2>
    <p>${c(t.description||"无描述")}</p>
    <h3>成员 (${t.members.length})</h3>
    <ul class="detail-list">
      ${t.members.map(n=>`<li>${c(n.userId)} <span class="role-tag">${c(n.role)}</span></li>`).join("")}
    </ul>
    <h3>专家 (${t.experts.length})</h3>
    <ul class="detail-list">
      ${t.experts.map(n=>`<li>${c(n.expertId)} ${n.enabled?"✅":"❌"}</li>`).join("")}
    </ul>
    <div class="actions">
      <button class="primary" id="btn-goto-chat">进入对话</button>
    </div>
  `,document.getElementById("btn-goto-chat").onclick=()=>{s.taskId?L(t.id,s.taskId):O()}}async function yt(t){let e=[];try{e=await at()}catch{}const n=document.getElementById("tab-content");new Set(t.experts.filter(a=>a.enabled).map(a=>a.expertId)),n.innerHTML=`
    <h2>项目配置</h2>

    <h3>基本信息</h3>
    <div class="form-row">
      <label>名称 <input id="cfg-name" value="${c(t.name)}"></label>
      <label>描述 <input id="cfg-desc" value="${c(t.description||"")}"></label>
      <button class="primary" id="btn-save-info">保存</button>
    </div>

    <h3>已配置的专家</h3>
    ${t.experts.length===0?'<p class="hint">暂无专家</p>':""}
    <div class="expert-grid">
      ${t.experts.map(a=>{const i=e.find(r=>r.id===a.expertId);return`
        <div class="expert-card added">
          <div class="expert-name">${c((i==null?void 0:i.name)||a.expertId)}</div>
          <div class="expert-id">${c(a.expertId)}</div>
          <div class="expert-desc">${c((i==null?void 0:i.description)||"")}</div>
          <div class="expert-actions">
            <label><input type="checkbox" class="toggle-expert" data-expert="${c(a.expertId)}" ${a.enabled?"checked":""}> 启用</label>
            <button class="small danger btn-remove-expert" data-expert="${c(a.expertId)}">移除</button>
          </div>
        </div>`}).join("")}
    </div>
    <button class="primary" id="btn-add-expert">+ 添加专家</button>

    <h3>成员</h3>
    <div id="member-list">
      ${t.members.map(a=>`
        <div class="member-row">
          <span>${c(a.userId)}</span>
          ${a.role!=="OWNER"?`<select class="role-select" data-user="${c(a.userId)}">
                <option value="ADMIN" ${a.role==="ADMIN"?"selected":""}>ADMIN</option>
                <option value="MEMBER" ${a.role==="MEMBER"?"selected":""}>MEMBER</option>
                <option value="VIEWER" ${a.role==="VIEWER"?"selected":""}>VIEWER</option>
              </select>
              <button class="small danger btn-remove-member" data-user="${c(a.userId)}">移除</button>`:'<span class="role-tag">OWNER</span>'}
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
  `,document.getElementById("btn-save-info").onclick=async()=>{const a=o("cfg-name").value.trim(),i=o("cfg-desc").value.trim();if(a)try{await G(t.id,{name:a,description:i}),p=await g(t.id),h("settings")}catch(r){alert(String(r))}},document.getElementById("btn-add-expert").onclick=()=>{const a=new Set(t.experts.map(r=>r.expertId)),i=e.filter(r=>!a.has(r.id));o("dialog-overlay").innerHTML=`
      <div class="dialog wide">
        <h3>添加专家</h3>
        ${i.length===0?"<p>所有专家已添加</p>":""}
        <div style="max-height:300px;overflow-y:auto">
        ${i.map(r=>`
          <div class="expert-card" style="cursor:pointer" data-add-expert="${c(r.id)}">
            <div class="expert-name">${c(r.name)}</div>
            <div class="expert-id">${c(r.id)}</div>
            <div class="expert-desc">${c(r.description)}</div>
          </div>
        `).join("")}
        </div>
        <div class="dialog-actions">
          <button id="dlg-cancel">取消</button>
        </div>
      </div>`,o("dialog-overlay").style.display="flex",o("dlg-cancel").onclick=()=>o("dialog-overlay").style.display="none",document.querySelectorAll("[data-add-expert]").forEach(r=>{r.addEventListener("click",async()=>{const l=r.dataset.addExpert;try{await D(t.id,l,!0),p=await g(t.id),o("dialog-overlay").style.display="none",h("settings")}catch(b){alert(String(b))}})})},n.querySelectorAll(".toggle-expert").forEach(a=>{a.addEventListener("change",async()=>{const i=a.dataset.expert,r=a.checked;try{await D(t.id,i,r),p=await g(t.id)}catch(l){alert(String(l))}})}),n.querySelectorAll(".btn-remove-expert").forEach(a=>{a.addEventListener("click",async()=>{const i=a.dataset.expert;try{await it(t.id,i),p=await g(t.id),h("settings")}catch(r){alert(String(r))}})}),document.getElementById("btn-add-member").onclick=async()=>{const a=o("new-member-id").value.trim(),i=o("new-member-role").value;if(a)try{await B(t.id,a,i),p=await g(t.id),h("settings")}catch(r){alert(String(r))}},n.querySelectorAll(".role-select").forEach(a=>{a.addEventListener("change",async()=>{const i=a.dataset.user,r=a.value;try{await B(t.id,i,r),p=await g(t.id),h("settings")}catch(l){alert(String(l))}})}),n.querySelectorAll(".btn-remove-member").forEach(a=>{a.addEventListener("click",async()=>{const i=a.dataset.user;try{await st(t.id,i),p=await g(t.id),h("settings")}catch(r){alert(String(r))}})})}function L(t,e){var n;(n=s.stream)==null||n.disconnect(),s.projectId=t,s.taskId=e,s.pendingHumanRequest=null,m(),u(),R()}async function R(){if(!s.projectId||!s.taskId){k();return}y=null,o("main-content").innerHTML=`
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
  `;const t=o("chat-input");t.addEventListener("input",()=>{t.style.height="auto",t.style.height=Math.min(t.scrollHeight,120)+"px"}),t.addEventListener("keydown",e=>{e.key==="Enter"&&!e.shiftKey&&(e.preventDefault(),o("chat-form").requestSubmit())});try{const e=await Z(s.projectId,s.taskId),n=s.tasks.get(s.projectId)||[];n.find(i=>i.taskId===e.taskId)||(n.push(e),s.tasks.set(s.projectId,n),u());const a=document.getElementById("chat-title");a&&(a.textContent=e.title)}catch(e){const n=(e==null?void 0:e.message)||String(e);if(n.includes("VIEWER")||n.includes("FORBIDDEN")||n.includes("403")){s.taskId="",m(),o("main-content").innerHTML=`
        <div class="panel" style="margin-top:60px;text-align:center">
          <h3>权限不足</h3>
          <p>你当前是 VIEWER 角色，只能查看项目，不能进入对话。</p>
          <button id="btn-back-project" class="primary" style="margin-top:12px">返回项目</button>
        </div>
      `,document.getElementById("btn-back-project").onclick=()=>E(s.projectId),u();return}const a=document.getElementById("chat-title");a&&(a.textContent="对话")}ht(),o("chat-form").addEventListener("submit",async e=>{e.preventDefault();const n=t.value.trim();if(n){t.value="",t.style.height="auto";try{await et(s.projectId,s.taskId,n)}catch(a){vt("error",String(a),"system")}}})}function ht(){var n;(n=s.stream)==null||n.disconnect(),s.stream=new rt(s.projectId,s.taskId),s.stream.setLastSequence(0),y=null;const t=document.getElementById("chat-timeline");t&&(t.innerHTML=""),s.stream.onEvent(a=>{bt(a)}),s.stream.connect();const e=document.getElementById("connection-status");e&&(e.className="dot online",e.textContent="已连接")}function vt(t,e,n){const a=document.getElementById("chat-timeline");a&&(a.insertAdjacentHTML("beforeend",`<div class="bubble ${t}">
      <div class="meta">${c(n)} · ${S(Date.now())}</div>
      <div class="text">${c(e)}</div>
    </div>`),a.scrollTop=a.scrollHeight)}let y=null,x="";function bt(t){const e=document.getElementById("chat-timeline");if(!e)return;const n=t.type||"";if((n==="confirm"||n==="coordinatorConfirm")&&(s.pendingHumanRequest={id:t.questionId||"",question:t.content||"Agent 需要你的确认",type:"CLARIFICATION",status:"PENDING"},It(t)),n==="userMessage"){y=null,x="";const r=t.agentId||"user",l=r===$().user;e.insertAdjacentHTML("beforeend",`<div class="bubble ${l?"user":"other"}">
        <div class="meta">${c(r)} · ${S(t.timestamp)}</div>
        <div class="text">${c(t.content||"")}</div>
      </div>`),e.scrollTop=e.scrollHeight;return}let a="";if(n==="thinkingDelta"||n==="thinking")a=t.text||"";else if(n==="textDelta")a=t.text||"";else if(n==="chat"||n==="coordinatorChat")a=t.content||"";else if(n==="end")(t.content||"").startsWith("{")||(a=t.content||"");else if(n==="coordinatorPhase"||n==="liveStatus"||n==="coordinatorPlanUpdate"||n==="coordinatorNewPlanStep"||n==="coordinatorError"||n==="planUpdate"||n==="newPlanStep"||n==="error"||n==="taskInQueue"){const r=ft({type:n,...t});if(a=t.content||t.text||r||"",!a&&(n==="coordinatorPlanUpdate"||n==="planUpdate")){const l=t.tasks;l!=null&&l.length&&(a="计划: "+l.map(b=>b.title).join(", "))}}if(!a)return;t.agentId&&t.agentId!==x&&(x=t.agentId),y||(y=document.createElement("div"),y.className="bubble system",y.innerHTML=`
      <div class="reply-agent">${c(x)}</div>
      <div class="reply-output"></div>
    `,e.appendChild(y));const i=y.querySelector(".reply-output");n==="thinkingDelta"||n==="thinking"||n==="textDelta"?i.textContent+=a:n==="chat"||n==="coordinatorChat"||n==="end"&&!(t.content||"").startsWith("{")?(i.textContent&&(i.textContent+=`

`),i.textContent+=a):(i.textContent&&(i.textContent+=`
`),i.textContent+="["+S(t.timestamp)+"] "+a),e.scrollTop=e.scrollHeight}function ft(t){const e=t,n=e.type||"",a=e.status||"",i=e.content||e.text||"",r={userMessage:"用户消息",coordinatorPhase:"阶段",coordinatorChat:"回复",coordinatorConfirm:"需要确认",coordinatorError:"错误",coordinatorPlanUpdate:"计划更新",coordinatorNewPlanStep:"任务开始",coordinatorRunCancelled:"已取消",liveStatus:"",thinkingStart:"开始思考",thinkingDelta:"思考中",thinking:"思考",thinkingEnd:"思考结束",textDelta:"输出中",streamStart:"开始输出",streamEnd:"输出结束",chat:"回复",end:"完成",error:"错误",confirm:"需要确认",planUpdate:"计划更新",newPlanStep:"任务开始",taskInQueue:"排队中",toolUsed:"工具调用",toolResult:"工具结果",weblink:"打开链接",file:"文件",directory:"目录",sidebarDisplay:"工作区",clearBoundary:"上下文清理",compactBoundary:"上下文压缩",reconnect:"重连"};return n==="coordinatorPhase"?{analyzing:"正在理解需求",planning:"正在制定计划",dispatching:"正在分配专家",answering:"正在整理回复",waiting_human:"需要确认",completed:"已完成",failed:"执行失败"}[a]||i||"阶段转换":n==="liveStatus"?i||"状态更新":r[n]||i||null}function It(t){const e=document.getElementById("hitl-panel");if(!e)return;const n=t.content||"Agent 需要你的确认",a=t.questions;e.className="hitl-panel",a!=null&&a.length?(e.innerHTML=a.map(i=>{var r;return`
      <div class="hitl-question">
        <strong>${c(i.header||i.question)}</strong>
        <p>${c(i.question)}</p>
        ${(r=i.options)!=null&&r.length?`<div>${i.options.map(l=>`<label class="hitl-option"><input type="${i.multiSelect?"checkbox":"radio"}" name="hitl-${c(i.header)}" value="${c(l.label)}"> ${c(l.label)} — ${c(l.description)}</label>`).join("")}</div>`:`<input class="hitl-input" data-question="${c(i.question)}" placeholder="输入你的回答...">`}
      </div>`}).join("")+'<button class="primary" id="hitl-submit">提交回答</button>',o("hitl-submit").onclick=async()=>{const i={};e.querySelectorAll(".hitl-input").forEach(r=>{const l=r;i[l.dataset.question||"answer"]=l.value.trim()}),e.querySelectorAll("input:checked").forEach(r=>{i[r.name.replace("hitl-","")]=r.value}),await A(i)}):(e.innerHTML=`
      <strong>${c(n)}</strong>
      <input id="hitl-answer" placeholder="输入回答...">
      <button class="primary" id="hitl-submit">提交</button>
    `,o("hitl-submit").onclick=async()=>{const i=o("hitl-answer").value.trim();i&&await A({answer:i})})}async function A(t){const e=s.pendingHumanRequest;if(e)try{await nt(s.projectId,e.id,"ANSWER",t),s.pendingHumanRequest=null;const n=document.getElementById("hitl-panel");n&&(n.className="hitl-panel hidden")}catch(n){alert(String(n))}}document.addEventListener("DOMContentLoaded",ot);
