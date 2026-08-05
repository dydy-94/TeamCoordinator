//　用戶消息
data:{"type":"userInput","sessionId":"xxxxxxxx","data":{"projectName":"3333","skills":["cmb-ui-design"], "sessionParams": {"knowledgeBaseNames": ["sre_kb"]},"contents":[{"type":"text","value":"123"}],"attachments":[{"fileName":"start-with-nginx.txt","contentType":"application/pdf","pathType":"input","path":"start-with-nginx.txt"}],"feUserInputMessageId":""},"timestamp":1769482300130}
{
  "type": "userInput",
  "sessionId": "xxxxxxxx",
  "data": {
    "projectName": "3333",
    "skills": [
      "cmb-ui-design"
    ],
    "sessionParams": {
      "kbNames": ["sre_kb"], //用户激活的知识库名称
    },
    "contents": [
      {
        "type": "text",
        "value": "123"
      }
    ],
    "attachments": [
      {
        "fileName": "start-with-nginx.txt",
        "contentType": "application/pdf",
        "pathType": "input",
        "path": "start-with-nginx.txt"
      }
    ],
    "feUserInputMessageId": ""
  },
  "timestamp": 1769482300130
}

//排队等待中
data:{"type":"taskInQueue","content": "由于平台模型请求量较高，您目前排在第24位。感谢耐心等待，我们会尽快为您处理。","sessionId":"115","taskId":"115","timestamp": 1769482300130}

//用户回应
{"type":"userAnswerQuestion","sessionId":"115","data":{"questionId":"90ffa9dd-95aa-4338-a731-8bb983916536","answers":{"写作主题":"武侠","写作风格":"简洁直白"}}}

//状态变化
data:{"type":"liveStatus","content": "初始化","timestamp": ***敏感信息系统已自动屏蔽***30}

data:{"type":"liveStatus","content": "思考中","timestamp": ***敏感信息系统已自动屏蔽***30}

data:{"type":"liveStatus","content": "请求模型","timestamp": ***敏感信息系统已自动屏蔽***30}

data:{"type":"liveStatus","content": "模型思考中","timestamp": ***敏感信息系统已自动屏蔽***30}

data:{"type":"liveStatus","content": "模型响应中","timestamp": ***敏感信息系统已自动屏蔽***30}

data:{"type":"liveStatus","content": "工具调用中","timestamp": ***敏感信息系统已自动屏蔽***30}

//异常
data:{"type": "error", "content": "xxxxx","timestamp": 1769482300130}

//更新状态
data:{"type": "planUpdate","timestamp","timestamp": 1769482307772, "tasks": [{"status":"doing","title":"初始化 Web 项目并了解项目结构","startedAt":1769482307772},{"status":"todo","title":"设计和开发登录界面","startedAt":0},{"status":"todo","title":"部署和测试网站","startedAt":0},{"status":"todo","title":"向用户交付最终成果","startedAt":0}]}



//newPlanStep
{"type":"newPlanStep","timestamp":1769482307773,"content":"初始化 Web 项目并了解项目结构"}

//询问用户问题
{"type":"confirm","questionId":"111","questions":[{"question":"您想要创建什么类型的 React 项目？","header":"项目类型","options":[{"label":"企业级管理系统","description":"包含用户管理、权限控制、数据看板等完整功能的后台管理系统"},{"label":"电商网站","description":"商品展示、购物车、订单管理等功能的在线商城"}],"multiSelect":false},{"question":"您希望使用哪种 UI 方案？","header":"UI 方案","options":[{"label":"Ant Design","description":"企业级 UI 组件库，功能丰富，文档完善"}],"multiSelect":false}]}

//删除
{"type":"end","fileType":"common","timestamp":1774855789217,"attachments":[],"usage":{"input_tokens":18780,"output_tokens":142},"eventId":"d3d1e201-6da3-4a43-96bb-8fba343ab165"}

//工作区展示内容
{"type":"sidebarDisplay","timestamp":1769482309239,"mode":"excalidraw/vnc"}

//带附件的、带建议,fileType:输出的文件类型是普通文件还是web项目
{"type":"chat","content":"xxxxx","timestamp":1769482300130,"usage":{"input_tokens":18780,"output_tokens":142},"fileType":"common/webProject", "attachments":[{"fileName":"hello.pdf","contentType":"application/pdf","path":"/home/abc/hello.pdf"}],"suggetions":["",""]}

{
  "type": "chat",
  "content": "xxxxx",
  "timestamp": 1769482300130,
  "fileType": "common/webProject",
  "attachments": [
    {
      "fileName": "hello.pdf",
      "contentType": "application/pdf",
      "pathType": "input/output/absolute",
      "path": "/home/abc/hello.pdf"
    }
  ],
  "suggetions": [
    "",
    ""
  ]
}

//打开链接
{"type":"weblink","content":"工作区打开链接","timestamp":"Date.now()","path":"url"}
{"type":"reconnect","content":"dfsfxxx","timestamp":"Date.now()","path":"url"}


//流式消息开始标识
{"type": "streamStart", "timestamp": 1223333, "blockType" :"text ,当前暂只支持文本"}
//文本流
{"type": "textDelta", "timestamp": 1223333, "text": "hello"}
//流式消息结束标识
{"type":"streamEnd", "timestamp": 1233, "totalTime": 12333}

{"type":"file", "fileName":"hello.pdf","contentType":"application/pdf","path":"/home/abc/hello.pdf"}

{"type":"directory", "name":"abc","path":"/home/abc"}



//流式思考消息开始标识
{"type": "thinkingStart", "timestamp": 1223333, "blockType" :"text ,当前暂只支持文本"}
//思考流

{"type": "thinkingDelta", "timestamp": 1223333, "text": "hello"}
//全量思考内容
{"type": "thinking", "timestamp": 1223333, "text": "hello","usage":{"input_tokens":18780,"output_tokens":142}}
//流式思考结束标识
{"type":"thinkingEnd", "timestamp": 1233, "totalTime": 12333}

//清理上下文界限
{"type":"clearBoundary","timestamp":1769482309239}

// 压缩界限
{"type":"compactBoundary","timestamp":1769482309239}

//对话, 如果是子agent parentToolUseId 不为空
{"type": "chat", "content": "xxxxx","timestamp": 1769482300130, "usage":{"input_tokens":18780,"output_tokens":142}, "parentToolUseId": "子agent 的toolUseID"}

//toolUsed，如果属于子agent parentToolUseId 不为空
{"type":"toolUsed","content":"正在使用工具Skill","tool":"Skill","input":{"skill":"icode:vuln-resolution"},"toolUseId":"call_6e50da019a944617b5a8a64a","timestamp":1770739112746, "parentToolUseId": "子agent 的toolUseID"}

//toolResult，如果属于子agent parentToolUseId 不为空
{"type":"toolResult","toolName":"Skill","toolUseId":"call_6e50da019a944617b5a8a64a","input":{"skill":"icode:vuln-resolution"},"output":"Launching skill: icode:vuln-resolution","timestamp":1770739112763, "parentToolUseId": "子agent 的toolUseID"}

//子agent 的思考过程
{"type": "subagentThinking", "timestamp": 1223333, "text": "hello","usage":{"input_tokens":18780,"output_tokens":142}, "parentToolUseId": "子agent 的toolUseID"}

//子agent 的文字回复
{"type": "subagentChat", "content": "xxxxx","timestamp": 1769482300130, "usage":{"input_tokens":18780,"output_tokens":142}, "parentToolUseId": "子agent 的toolUseID"}

//子agent 的 toolUsed
{"type":"subagentToolUsed","content":"正在使用工具Skill","tool":"Skill","input":{"skill":"icode:vuln-resolution"},"toolUseId":"call_6e50da019a944617b5a8a64a","timestamp":1770739112746,"parentToolUseId": "子agent 的toolUseID"}

// 子agent 的 toolResult
{"type":"subagentToolResult","toolName":"Skill","toolUseId":"call_6e50da019a944617b5a8a64a","input":{"skill":"icode:vuln-resolution"},"output":"Launching skill: icode:vuln-resolution","timestamp":1770739112763, "parentToolUseId": "子agent 的toolUseID"}

//流式写文件,toolUseId 对应write 工具的toolUseId， 如果属于子agent parentToolUseId 不为空
{"type":"streamingFile", "fileName": "hello.txt", "contentType":"application/pdf","path":"/home/abc/hello.pdf", "toolUseId":"call_6e50da019a944617b5a8a64a","parentToolUseId": "子agent 的tooluseID","timestamp":1769482300130}