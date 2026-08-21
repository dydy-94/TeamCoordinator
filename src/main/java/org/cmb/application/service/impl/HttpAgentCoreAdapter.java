package org.cmb.application.service.impl;
import org.cmb.application.domain.AgentRunResponse;
import org.cmb.application.domain.AgentRunRequest;
import org.cmb.application.domain.AgentEvent;
import org.cmb.application.domain.AgentCoreConversationResponse;
import org.cmb.application.domain.AgentCoreConversationRequest;
import org.cmb.application.service.AgentCoreAdapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.cmb.common.config.DigitalTeamProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(
        prefix = "digital-team.agent-core",
        name = "mock-enabled",
        havingValue = "false")
public class HttpAgentCoreAdapter implements AgentCoreAdapter {

    private final DigitalTeamProperties.AgentCore properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpAgentCoreAdapter(
            DigitalTeamProperties properties, ObjectMapper objectMapper) {
        this(properties.getAgentCore(), objectMapper, restTemplate(properties.getAgentCore()));
    }

    public HttpAgentCoreAdapter(
            DigitalTeamProperties.AgentCore properties,
            ObjectMapper objectMapper,
            RestTemplate restTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        if (properties.getBaseUrl() == null || properties.getBaseUrl().trim().isEmpty()) {
            throw new IllegalStateException(
                    "digital-team.agent-core.base-url is required when mock is disabled.");
        }
    }

    @Override
    public AgentRunResponse submitRun(String targetAgentId, AgentRunRequest request) {
        AgentCoreConversationResponse body = restTemplate.exchange(
                uri(agentPath(properties.getSubmitPath(), targetAgentId, null)),
                HttpMethod.POST,
                new HttpEntity<>(
                        AgentCoreConversationRequest.userInput(request), jsonHeaders(null)),
                AgentCoreConversationResponse.class).getBody();
        return requireResponse(body);
    }

    @Override
    public List<AgentEvent> streamEvents(
            String targetAgentId, String sessionId, Long afterSequence) {
        return streamEvents(targetAgentId, sessionId, afterSequence, null);
    }

    @Override
    public List<AgentEvent> streamEvents(
            String targetAgentId, String sessionId, Long afterSequence,
            String businessSessionId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(
                uri(agentPath(properties.getStreamPath(), targetAgentId, sessionId)));
        if (afterSequence != null) {
            builder.queryParam("afterSequence", afterSequence);
        }
        HttpHeaders headers = jsonHeaders(businessSessionId);
        headers.setAccept(Collections.singletonList(MediaType.TEXT_EVENT_STREAM));
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    builder.build(true).toUri(),
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    String.class);
            return parseSse(
                    response.getBody(), sessionId, afterSequence == null ? 0L : afterSequence);
        } catch (HttpClientErrorException.NotFound ex) {
            // A missing run is not a transport failure: surface it as an
            // empty stream so the caller's lost-run detection decides
            // (with its consecutive-failure tolerance) what to do.
            return Collections.emptyList();
        }
    }

    @Override
    public AgentEvent getRunStatus(String targetAgentId, String sessionId) {
        return getRunStatus(targetAgentId, sessionId, null);
    }

    @Override
    public AgentEvent getRunStatus(
            String targetAgentId, String sessionId, String businessSessionId) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    uri(agentPath(properties.getStatusPath(), targetAgentId, sessionId)),
                    HttpMethod.GET,
                    new HttpEntity<Void>(jsonHeaders(businessSessionId)),
                    JsonNode.class);
            JsonNode body = response.getBody();
            return body == null ? null : toAgentEvent(body, sessionId, 0L);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    @Override
    public AgentEvent cancelRun(String targetAgentId, String sessionId) {
        return cancelRun(targetAgentId, sessionId, null);
    }

    @Override
    public AgentEvent cancelRun(
            String targetAgentId, String sessionId, String businessSessionId) {
        try {
            AgentRunResponse response = stopSession(targetAgentId, sessionId);
            AgentEvent event = new AgentEvent();
            event.setSessionId(sessionId);
            event.setType("RUN_CANCELLED");
            event.setStatus(response == null ? "FAILED" : "CANCELLED");
            event.setContent("AgentCore session stopped.");
            event.setTimestamp(System.currentTimeMillis());
            return event;
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    @Override
    public AgentRunResponse stopSession(String targetAgentId, String sessionId) {
        try {
            ResponseEntity<AgentCoreConversationResponse> response = restTemplate.exchange(
                    uri(agentPath(properties.getSubmitPath(), targetAgentId, sessionId)),
                    HttpMethod.POST,
                    new HttpEntity<>(
                            AgentCoreConversationRequest.stopSession(sessionId),
                            jsonHeaders(null)),
                    AgentCoreConversationResponse.class);
            return requireResponse(response.getBody());
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    @Override
    public AgentRunResponse resumeRun(
            String targetAgentId, String sessionId,
            String humanResponse, String idempotencyKey) {
        return resumeRun(targetAgentId, sessionId, humanResponse, idempotencyKey, null);
    }

    @Override
    public boolean deleteSession(String targetAgentId, String sessionId) {
        try {
            restTemplate.exchange(
                    uri(agentPath(properties.getSubmitPath(), targetAgentId, sessionId)),
                    HttpMethod.POST,
                    new HttpEntity<>(
                            AgentCoreConversationRequest.deleteSession(sessionId),
                            jsonHeaders(null)),
                    AgentCoreConversationResponse.class);
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
    }

    @Override
    public AgentRunResponse resumeRun(
            String targetAgentId, String sessionId,
            String humanResponse, String idempotencyKey,
            String businessSessionId) {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("answer", humanResponse);
        return answerQuestion(targetAgentId, sessionId, idempotencyKey, answers);
    }

    @Override
    public AgentRunResponse answerQuestion(
            String targetAgentId, String sessionId,
            String questionId, Map<String, String> answers) {
        ResponseEntity<AgentCoreConversationResponse> response = restTemplate.exchange(
                uri(agentPath(properties.getSubmitPath(), targetAgentId, sessionId)),
                HttpMethod.POST,
                new HttpEntity<>(
                        AgentCoreConversationRequest.answerQuestion(
                                sessionId, questionId, answers),
                        jsonHeaders(null)),
                AgentCoreConversationResponse.class);
        return requireResponse(response.getBody());
    }

    // ── SSE Parsing ─────────────────────────────────────────────────────

    private List<AgentEvent> parseSse(String body, String sessionId, long afterSequence) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentEvent> result = new ArrayList<>();
        StringBuilder streamedText = new StringBuilder();
        String latestChat = null;
        long sequence = afterSequence;
        String normalized = body.replace("\r\n", "\n");
        for (String block : normalized.split("\\n\\n")) {
            String id = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\\n")) {
                if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
            }
            if (data.length() == 0) {
                continue;
            }
            try {
                JsonNode raw = objectMapper.readTree(data.toString());
                AgentEvent event = toAgentEvent(raw, sessionId, ++sequence);
                if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                    event.setEventId(id);
                }
                // Accumulate streaming text
                if ("textDelta".equals(event.getType()) && event.getText() != null) {
                    streamedText.append(event.getText());
                }
                // Track latest chat content
                if ("chat".equals(event.getType()) && event.getContent() != null) {
                    latestChat = event.getContent();
                }
                // On end, inject accumulated result text if chat didn't provide it
                if ("end".equals(event.getType())) {
                    String resultText = latestChat != null
                            ? latestChat : streamedText.toString();
                    if (event.getContent() == null && !resultText.isEmpty()) {
                        event.setContent(resultText);
                    }
                }
                if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                    throw new IllegalStateException(
                            "AgentCore SSE event did not contain eventId.");
                }
                result.add(event);
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Could not parse AgentCore SSE event.", ex);
            }
        }
        return result;
    }

    /**
     * Convert a raw AgentCore SSE JSON node to an AgentEvent, preserving the
     * original {@code type} and mapping all known fields.
     */
    private AgentEvent toAgentEvent(JsonNode raw, String queriedSessionId, long sequence) {
        String rawType = text(raw, "type");
        AgentEvent event = new AgentEvent();
        event.setType(rawType != null ? rawType : "unknown");
        event.setSessionId(
                raw.hasNonNull("sessionId") ? text(raw, "sessionId") : queriedSessionId);
        event.setEventId(text(raw, "eventId"));
        event.setSequence(sequence);
        event.setTimestamp(raw.hasNonNull("timestamp") ? raw.get("timestamp").asLong() : 0L);

        // Map fields per type
        switch (rawType == null ? "" : rawType) {
            case "chat":
                event.setContent(text(raw, "content"));
                if (raw.hasNonNull("fileType")) {
                    event.setFileType(text(raw, "fileType"));
                }
                if (raw.hasNonNull("attachments")) {
                    event.setAttachments(parseAttachments(raw.get("attachments")));
                }
                if (raw.hasNonNull("suggestions")) {
                    event.setSuggestions(parseStringList(raw.get("suggestions")));
                }
                if (raw.hasNonNull("usage")) {
                    event.setUsage(parseUsage(raw.get("usage")));
                }
                if (raw.hasNonNull("parentToolUseId")) {
                    event.setParentToolUseId(text(raw, "parentToolUseId"));
                }
                break;
            case "textDelta":
                event.setText(text(raw, "text"));
                break;
            case "streamStart":
                event.setBlockType(text(raw, "blockType"));
                break;
            case "streamEnd":
                event.setTotalTime(raw.hasNonNull("totalTime")
                        ? raw.get("totalTime").asInt() : null);
                break;
            case "thinkingStart":
                event.setBlockType(text(raw, "blockType"));
                break;
            case "thinkingDelta":
                event.setText(text(raw, "text"));
                break;
            case "thinking":
                event.setText(text(raw, "text"));
                if (raw.hasNonNull("usage")) {
                    event.setUsage(parseUsage(raw.get("usage")));
                }
                break;
            case "thinkingEnd":
                event.setTotalTime(raw.hasNonNull("totalTime")
                        ? raw.get("totalTime").asInt() : null);
                break;
            case "planUpdate":
                if (raw.hasNonNull("tasks")) {
                    event.setTasks(parsePlanTasks(raw.get("tasks")));
                }
                break;
            case "newPlanStep":
                event.setContent(text(raw, "content"));
                break;
            case "confirm":
                event.setContent(text(raw, "content"));
                event.setQuestionId(text(raw, "questionId"));
                if (raw.hasNonNull("questions")) {
                    event.setQuestions(parseQuestions(raw.get("questions")));
                }
                break;
            case "end":
                event.setContent(text(raw, "content"));
                if (raw.hasNonNull("fileType")) {
                    event.setFileType(text(raw, "fileType"));
                }
                if (raw.hasNonNull("attachments")) {
                    event.setAttachments(parseAttachments(raw.get("attachments")));
                }
                if (raw.hasNonNull("usage")) {
                    event.setUsage(parseUsage(raw.get("usage")));
                }
                break;
            case "error":
                event.setContent(text(raw, "content"));
                break;
            case "taskInQueue":
                event.setContent(text(raw, "content"));
                break;
            case "liveStatus":
                event.setContent(text(raw, "content"));
                break;
            case "toolUsed":
                event.setContent(text(raw, "content"));
                event.setTool(text(raw, "tool"));
                event.setToolUseId(text(raw, "toolUseId"));
                event.setParentToolUseId(text(raw, "parentToolUseId"));
                if (raw.hasNonNull("input")) {
                    event.setInput(objectMapper.convertValue(
                            raw.get("input"), Map.class));
                }
                break;
            case "toolResult":
                event.setTool(text(raw, "toolName"));
                event.setToolUseId(text(raw, "toolUseId"));
                event.setParentToolUseId(text(raw, "parentToolUseId"));
                event.setOutput(text(raw, "output"));
                if (raw.hasNonNull("input")) {
                    event.setInput(objectMapper.convertValue(
                            raw.get("input"), Map.class));
                }
                break;
            case "subagentThinking":
                event.setText(text(raw, "text"));
                event.setParentToolUseId(text(raw, "parentToolUseId"));
                if (raw.hasNonNull("usage")) {
                    event.setUsage(parseUsage(raw.get("usage")));
                }
                break;
            case "subagentChat":
                event.setContent(text(raw, "content"));
                event.setParentToolUseId(text(raw, "parentToolUseId"));
                if (raw.hasNonNull("usage")) {
                    event.setUsage(parseUsage(raw.get("usage")));
                }
                break;
            case "subagentToolUsed":
                event.setContent(text(raw, "content"));
                event.setTool(text(raw, "tool"));
                event.setToolUseId(text(raw, "toolUseId"));
                event.setParentToolUseId(text(raw, "parentToolUseId"));
                if (raw.hasNonNull("input")) {
                    event.setInput(objectMapper.convertValue(
                            raw.get("input"), Map.class));
                }
                break;
            case "subagentToolResult":
                event.setTool(text(raw, "toolName"));
                event.setToolUseId(text(raw, "toolUseId"));
                event.setParentToolUseId(text(raw, "parentToolUseId"));
                event.setOutput(text(raw, "output"));
                if (raw.hasNonNull("input")) {
                    event.setInput(objectMapper.convertValue(
                            raw.get("input"), Map.class));
                }
                break;
            case "file":
                event.setFileName(text(raw, "fileName"));
                event.setContentType(text(raw, "contentType"));
                event.setPath(text(raw, "path"));
                break;
            case "directory":
                event.setName(text(raw, "name"));
                event.setPath(text(raw, "path"));
                break;
            case "streamingFile":
                event.setFileName(text(raw, "fileName"));
                event.setContentType(text(raw, "contentType"));
                event.setPath(text(raw, "path"));
                event.setToolUseId(text(raw, "toolUseId"));
                event.setParentToolUseId(text(raw, "parentToolUseId"));
                break;
            case "sidebarDisplay":
                event.setMode(text(raw, "mode"));
                break;
            case "weblink":
                event.setContent(text(raw, "content"));
                event.setPath(text(raw, "path"));
                break;
            case "reconnect":
                event.setContent(text(raw, "content"));
                event.setPath(text(raw, "path"));
                break;
            case "clearBoundary":
            case "compactBoundary":
                // Pass through with timestamp only
                break;
            default:
                // Unknown types: capture generic content/message
                event.setContent(text(raw, "content"));
                if (event.getContent() == null) {
                    event.setContent(text(raw, "message"));
                }
                break;
        }
        return event;
    }

    // ── JSON helpers ────────────────────────────────────────────────────

    private List<AgentEvent.AttachmentInfo> parseAttachments(JsonNode array) {
        List<AgentEvent.AttachmentInfo> result = new ArrayList<>();
        for (JsonNode item : array) {
            AgentEvent.AttachmentInfo info = new AgentEvent.AttachmentInfo();
            info.setFileName(text(item, "fileName"));
            info.setContentType(text(item, "contentType"));
            info.setPathType(text(item, "pathType"));
            info.setPath(text(item, "path"));
            result.add(info);
        }
        return result;
    }

    private List<String> parseStringList(JsonNode array) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : array) {
            result.add(item.asText());
        }
        return result;
    }

    private AgentEvent.UsageInfo parseUsage(JsonNode node) {
        AgentEvent.UsageInfo usage = new AgentEvent.UsageInfo();
        if (node.hasNonNull("input_tokens")) {
            usage.setInputTokens(node.get("input_tokens").asInt());
        }
        if (node.hasNonNull("output_tokens")) {
            usage.setOutputTokens(node.get("output_tokens").asInt());
        }
        return usage;
    }

    private List<AgentEvent.PlanTaskStatus> parsePlanTasks(JsonNode array) {
        List<AgentEvent.PlanTaskStatus> result = new ArrayList<>();
        for (JsonNode item : array) {
            AgentEvent.PlanTaskStatus task = new AgentEvent.PlanTaskStatus();
            task.setStatus(text(item, "status"));
            task.setTitle(text(item, "title"));
            task.setStartedAt(item.hasNonNull("startedAt")
                    ? item.get("startedAt").asLong() : 0L);
            result.add(task);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<AgentEvent.Question> parseQuestions(JsonNode array) {
        List<AgentEvent.Question> result = new ArrayList<>();
        for (JsonNode item : array) {
            AgentEvent.Question q = new AgentEvent.Question();
            q.setQuestion(text(item, "question"));
            q.setHeader(text(item, "header"));
            q.setMultiSelect(item.hasNonNull("multiSelect") && item.get("multiSelect").asBoolean());
            if (item.hasNonNull("options")) {
                List<AgentEvent.Option> options = new ArrayList<>();
                for (JsonNode opt : item.get("options")) {
                    AgentEvent.Option o = new AgentEvent.Option();
                    o.setLabel(text(opt, "label"));
                    o.setDescription(text(opt, "description"));
                    options.add(o);
                }
                q.setOptions(options);
            }
            result.add(q);
        }
        return result;
    }

    // ── General helpers ─────────────────────────────────────────────────

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private AgentRunResponse requireResponse(AgentCoreConversationResponse body) {
        if (body == null) {
            throw new IllegalStateException("AgentCore returned an empty response.");
        }
        return body.toRunResponse();
    }

    private HttpHeaders jsonHeaders(String businessSessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getAuthValue() != null && !properties.getAuthValue().isEmpty()) {
            headers.set(properties.getAuthHeader(), properties.getAuthValue());
        }
        if (businessSessionId != null && !businessSessionId.trim().isEmpty()) {
            headers.set(properties.getSessionHeader(), businessSessionId);
        }
        return headers;
    }

    private URI uri(String path) {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String suffix = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + suffix);
    }

    private String agentPath(String template, String agentId, String sessionId) {
        String resolved = template.replace("{agentId}", agentId != null ? agentId : "_");
        if (sessionId != null) {
            resolved = resolved.replace("{sessionId}", sessionId);
        }
        return resolved;
    }

    private static RestTemplate restTemplate(DigitalTeamProperties.AgentCore properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
