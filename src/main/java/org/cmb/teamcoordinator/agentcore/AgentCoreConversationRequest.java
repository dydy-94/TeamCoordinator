package org.cmb.teamcoordinator.agentcore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentCoreConversationRequest {

    private String type;
    private String sessionId;
    private String systemPrompt;
    private Data data;

    public static AgentCoreConversationRequest userInput(AgentRunRequest run) {
        AgentCoreConversationRequest request = new AgentCoreConversationRequest();
        request.type = "userInput";
        request.sessionId = run.getConversationSessionId() != null
                ? run.getConversationSessionId() : "";
        request.systemPrompt = run.getSystemPrompt();
        Data input = new Data();
        input.skillNames = run.getSkillNames();
        input.skillOrigin = "skillDevelop";
        input.contents.add(new TextContent("text", run.getTaskText()));
        if (run.getStructuredInput() != null && !run.getStructuredInput().isEmpty()) {
            input.context.add(new TextContent("text", run.getContextText()));
        }
        input.attachments = run.getAttachments();
        request.data = input;
        return request;
    }

    public static AgentCoreConversationRequest stopSession(String sessionId) {
        AgentCoreConversationRequest request = new AgentCoreConversationRequest();
        request.type = "stopSession";
        request.sessionId = sessionId;
        return request;
    }

    public static AgentCoreConversationRequest answerQuestion(
            String sessionId, String questionId, Map<String, String> answers) {
        AgentCoreConversationRequest request = new AgentCoreConversationRequest();
        request.type = "userAnswerQuestion";
        request.sessionId = sessionId;
        Data input = new Data();
        input.questionId = questionId;
        input.answers = answers == null ? new LinkedHashMap<>() : answers;
        request.data = input;
        return request;
    }

    public String getType() { return type; }
    public void setType(String value) { this.type = value; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { this.sessionId = value; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String value) { this.systemPrompt = value; }
    public Data getData() { return data; }
    public void setData(Data value) { this.data = value; }

    public static class Data {
        private List<String> skillNames = new ArrayList<>();
        private String skillOrigin;
        private List<TextContent> contents = new ArrayList<>();
        private List<TextContent> context = new ArrayList<>();
        private List<AgentRunAttachment> attachments = new ArrayList<>();
        private String questionId;
        private Map<String, String> answers;

        public List<String> getSkillNames() { return skillNames; }
        public void setSkillNames(List<String> value) { this.skillNames = value; }
        public String getSkillOrigin() { return skillOrigin; }
        public void setSkillOrigin(String value) { this.skillOrigin = value; }
        public List<TextContent> getContents() { return contents; }
        public void setContents(List<TextContent> value) { this.contents = value; }
        public List<TextContent> getContext() { return context; }
        public void setContext(List<TextContent> value) { this.context = value; }
        public List<AgentRunAttachment> getAttachments() { return attachments; }
        public void setAttachments(List<AgentRunAttachment> value) { this.attachments = value; }
        public String getQuestionId() { return questionId; }
        public void setQuestionId(String value) { this.questionId = value; }
        public Map<String, String> getAnswers() { return answers; }
        public void setAnswers(Map<String, String> value) { this.answers = value; }
    }

    public static class TextContent {
        private String type;
        private String value;

        public TextContent() {
        }

        public TextContent(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() { return type; }
        public void setType(String value) { this.type = value; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
