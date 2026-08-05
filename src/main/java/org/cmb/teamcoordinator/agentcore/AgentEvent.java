package org.cmb.teamcoordinator.agentcore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Unified AgentCore SSE event model.
 *
 * <p>Covers all event types defined in {@code sse-result.md}. The {@code agentId}
 * field is a Coordinator addition that identifies which agent (coordinator or expert)
 * produced the event. Every other field maps 1:1 to an AgentCore SSE chunk property.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentEvent {

    // ── Core (all events) ──────────────────────────────────────────────
    private String type;         // AgentCore native type
    private String agentId;      // Coordinator addition: source agent identifier
    private String sessionId;    // AgentCore session ID
    private String eventId;      // Dedup key (maps to AgentCore eventId)
    private long sequence;       // Local ordering sequence assigned by Coordinator
    private long timestamp;      // AgentCore event timestamp (epoch ms)

    // ── Generic content ────────────────────────────────────────────────
    private String content;      // chat, liveStatus, error, newPlanStep, reconnect, weblink
    private String text;         // textDelta, thinkingDelta, thinking, subagentThinking
    private String status;       // Coordinator-internal task status (ACCEPTED, RUNNING, etc.)

    // ── confirm ────────────────────────────────────────────────────────
    private String questionId;
    private List<Question> questions;

    // ── planUpdate ─────────────────────────────────────────────────────
    private List<PlanTaskStatus> tasks;

    // ── chat ───────────────────────────────────────────────────────────
    private String fileType;           // "common" or "webProject"
    private List<AttachmentInfo> attachments;
    private List<String> suggestions;
    private UsageInfo usage;

    // ── toolUsed / toolResult ──────────────────────────────────────────
    private String tool;               // tool name
    private Map<String, Object> input; // tool input
    private String output;             // toolResult output
    @JsonProperty("toolUseId")
    private String toolUseId;
    @JsonProperty("parentToolUseId")
    private String parentToolUseId;    // non-null for sub-agent events

    // ── file / directory / streamingFile ───────────────────────────────
    private String fileName;
    private String contentType;
    private String path;               // file path or weblink URL
    private String name;               // directory name

    // ── sidebarDisplay ─────────────────────────────────────────────────
    private String mode;               // excalidraw, vnc, etc.

    // ── streamStart / thinkingStart ────────────────────────────────────
    private String blockType;          // "text"
    private Integer totalTime;         // streamEnd / thinkingEnd total ms

    // ── reconnect ──────────────────────────────────────────────────────
    // (reuses content + path)

    // ── Nested types ───────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Question {
        private String question;
        private String header;
        private List<Option> options;
        private boolean multiSelect;

        public String getQuestion() { return question; }
        public void setQuestion(String v) { this.question = v; }
        public String getHeader() { return header; }
        public void setHeader(String v) { this.header = v; }
        public List<Option> getOptions() { return options; }
        public void setOptions(List<Option> v) { this.options = v; }
        @JsonProperty("multiSelect")
        public boolean isMultiSelect() { return multiSelect; }
        public void setMultiSelect(boolean v) { this.multiSelect = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Option {
        private String label;
        private String description;

        public String getLabel() { return label; }
        public void setLabel(String v) { this.label = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanTaskStatus {
        private String status;     // "doing", "todo", "done"
        private String title;
        private long startedAt;

        public String getStatus() { return status; }
        public void setStatus(String v) { this.status = v; }
        public String getTitle() { return title; }
        public void setTitle(String v) { this.title = v; }
        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long v) { this.startedAt = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AttachmentInfo {
        private String fileName;
        private String contentType;
        private String pathType;   // "input", "output", "absolute"
        private String path;

        public String getFileName() { return fileName; }
        public void setFileName(String v) { this.fileName = v; }
        public String getContentType() { return contentType; }
        public void setContentType(String v) { this.contentType = v; }
        public String getPathType() { return pathType; }
        public void setPathType(String v) { this.pathType = v; }
        public String getPath() { return path; }
        public void setPath(String v) { this.path = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UsageInfo {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;

        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int v) { this.inputTokens = v; }
        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int v) { this.outputTokens = v; }
    }

    // ── Getters / Setters ──────────────────────────────────────────────

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String v) { this.agentId = v; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { this.sessionId = v; }

    public String getEventId() { return eventId; }
    public void setEventId(String v) { this.eventId = v; }

    public long getSequence() { return sequence; }
    public void setSequence(long v) { this.sequence = v; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long v) { this.timestamp = v; }

    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }

    public String getText() { return text; }
    public void setText(String v) { this.text = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String v) { this.questionId = v; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> v) { this.questions = v; }

    public List<PlanTaskStatus> getTasks() { return tasks; }
    public void setTasks(List<PlanTaskStatus> v) { this.tasks = v; }

    public String getFileType() { return fileType; }
    public void setFileType(String v) { this.fileType = v; }

    public List<AttachmentInfo> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentInfo> v) { this.attachments = v; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> v) { this.suggestions = v; }

    public UsageInfo getUsage() { return usage; }
    public void setUsage(UsageInfo v) { this.usage = v; }

    public String getTool() { return tool; }
    public void setTool(String v) { this.tool = v; }

    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> v) { this.input = v; }

    public String getOutput() { return output; }
    public void setOutput(String v) { this.output = v; }

    public String getToolUseId() { return toolUseId; }
    public void setToolUseId(String v) { this.toolUseId = v; }

    public String getParentToolUseId() { return parentToolUseId; }
    public void setParentToolUseId(String v) { this.parentToolUseId = v; }

    public String getFileName() { return fileName; }
    public void setFileName(String v) { this.fileName = v; }

    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }

    public String getPath() { return path; }
    public void setPath(String v) { this.path = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }

    public String getBlockType() { return blockType; }
    public void setBlockType(String v) { this.blockType = v; }

    public Integer getTotalTime() { return totalTime; }
    public void setTotalTime(Integer v) { this.totalTime = v; }

    // ── Factory helpers ────────────────────────────────────────────────

    /** Create a minimal AgentEvent with the given type. */
    public static AgentEvent of(String type) {
        AgentEvent e = new AgentEvent();
        e.type = type;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    /** Create an AgentEvent with type, content, and agentId. */
    public static AgentEvent content(String type, String content, String agentId) {
        AgentEvent e = of(type);
        e.content = content;
        e.agentId = agentId;
        return e;
    }

    /** Create an AgentEvent for streaming text. */
    public static AgentEvent textDelta(String text, String agentId) {
        AgentEvent e = of("textDelta");
        e.text = text;
        e.agentId = agentId;
        return e;
    }
}
