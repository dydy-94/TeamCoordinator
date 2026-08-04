package org.cmb.teamcoordinator.agentcore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.cmb.teamcoordinator.config.DigitalTeamProperties;
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
        ResponseEntity<AgentCoreConversationResponse> response = restTemplate.exchange(
                uri(properties.getSubmitPath()),
                HttpMethod.POST,
                new HttpEntity<>(
                        AgentCoreConversationRequest.userInput(request), jsonHeaders(null)),
                AgentCoreConversationResponse.class);
        return requireResponse(response);
    }

    @Override
    public List<AgentRunEvent> streamEvents(String sessionId, Long afterSequence) {
        return streamEvents(sessionId, afterSequence, null);
    }

    @Override
    public List<AgentRunEvent> streamEvents(
            String sessionId, Long afterSequence, String businessSessionId) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUri(uri(path(properties.getStreamPath(), sessionId)));
        if (afterSequence != null) {
            builder.queryParam("afterSequence", afterSequence);
        }
        HttpHeaders headers = jsonHeaders(businessSessionId);
        headers.setAccept(Collections.singletonList(MediaType.TEXT_EVENT_STREAM));
        ResponseEntity<String> response = restTemplate.exchange(
                builder.build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                String.class);
        return parseSse(
                response.getBody(), sessionId, afterSequence == null ? 0L : afterSequence);
    }

    @Override
    public AgentRunEvent getRunStatus(String sessionId) {
        return getRunStatus(sessionId, null);
    }

    @Override
    public AgentRunEvent getRunStatus(String sessionId, String businessSessionId) {
        try {
            ResponseEntity<AgentRunEvent> response = restTemplate.exchange(
                    uri(path(properties.getStatusPath(), sessionId)),
                    HttpMethod.GET,
                    new HttpEntity<Void>(jsonHeaders(businessSessionId)),
                    AgentRunEvent.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    @Override
    public AgentRunEvent cancelRun(String sessionId) {
        return cancelRun(sessionId, null);
    }

    @Override
    public AgentRunEvent cancelRun(String sessionId, String businessSessionId) {
        try {
            AgentRunResponse response = stopSession(sessionId);
            return new AgentRunEvent(
                    sessionId, 0, "RUN_CANCELLED",
                    response == null ? "FAILED" : "CANCELLED", "AgentCore session stopped.");
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    @Override
    public AgentRunResponse stopSession(String sessionId) {
        try {
            ResponseEntity<AgentCoreConversationResponse> response = restTemplate.exchange(
                    uri(properties.getSubmitPath()),
                    HttpMethod.POST,
                    new HttpEntity<>(
                            AgentCoreConversationRequest.stopSession(sessionId),
                            jsonHeaders(null)),
                    AgentCoreConversationResponse.class);
            return requireResponse(response);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    @Override
    public AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey) {
        return resumeRun(sessionId, humanResponse, idempotencyKey, null);
    }

    @Override
    public AgentRunResponse resumeRun(
            String sessionId, String humanResponse, String idempotencyKey,
            String businessSessionId) {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("answer", humanResponse);
        return answerQuestion(sessionId, idempotencyKey, answers);
    }

    @Override
    public AgentRunResponse answerQuestion(
            String sessionId, String questionId, Map<String, String> answers) {
        ResponseEntity<AgentCoreConversationResponse> response = restTemplate.exchange(
                uri(properties.getSubmitPath()),
                HttpMethod.POST,
                new HttpEntity<>(
                        AgentCoreConversationRequest.answerQuestion(
                                sessionId, questionId, answers),
                        jsonHeaders(null)),
                AgentCoreConversationResponse.class);
        return requireResponse(response);
    }

    private List<AgentRunEvent> parseSse(String body, String sessionId, long afterSequence) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentRunEvent> result = new ArrayList<>();
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
                String rawType = text(raw, "type");
                if ("textDelta".equals(rawType)) {
                    streamedText.append(text(raw, "text"));
                } else if ("chat".equals(rawType) && raw.hasNonNull("content")) {
                    latestChat = raw.get("content").asText();
                }
                AgentRunEvent event = toRunEvent(raw, sessionId, ++sequence);
                if ("RUN_SUCCEEDED".equals(event.getType())) {
                    String resultText = latestChat != null ? latestChat : streamedText.toString();
                    event.getPayload().put("resultText", resultText);
                }
                if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                    event.setEventId(id);
                }
                if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                    throw new IllegalStateException("AgentCore SSE event did not contain eventId.");
                }
                result.add(event);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not parse AgentCore SSE event.", ex);
            }
        }
        return result;
    }

    private AgentRunEvent toRunEvent(JsonNode raw, String queriedSessionId, long sequence) {
        String rawType = text(raw, "type");
        String type = "RUN_PROGRESS";
        String status = "RUNNING";
        String message = raw.hasNonNull("content")
                ? raw.get("content").asText() : rawType;
        if ("confirm".equals(rawType)) {
            type = "RUN_WAITING_HUMAN";
            status = "WAITING_HUMAN";
        } else if ("error".equals(rawType)) {
            type = "RUN_FAILED";
            status = "FAILED";
        } else if ("end".equals(rawType)) {
            type = "RUN_SUCCEEDED";
            status = "SUCCEEDED";
        } else if ("taskInQueue".equals(rawType)) {
            status = "QUEUED";
        }
        AgentRunEvent event = new AgentRunEvent(
                raw.hasNonNull("sessionId") ? text(raw, "sessionId") : queriedSessionId,
                sequence, type, status, message);
        event.setEventId(text(raw, "eventId"));
        Map<String, Object> payload = objectMapper.convertValue(raw, Map.class);
        payload.put("agentCoreType", rawType);
        if ("confirm".equals(rawType)) {
            payload.put("questionId", text(raw, "questionId"));
            payload.put("question", summarizeQuestions(raw.get("questions")));
            payload.put("requestType", "QUESTION");
        }
        event.setPayload(payload);
        return event;
    }

    private String summarizeQuestions(JsonNode questions) {
        if (questions == null || !questions.isArray()) {
            return "AgentCore requires user input.";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode question : questions) {
            values.add(text(question, "question"));
        }
        return String.join("\n", values);
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private AgentRunResponse requireResponse(
            ResponseEntity<AgentCoreConversationResponse> response) {
        if (response.getBody() == null) {
            throw new IllegalStateException("AgentCore returned an empty response.");
        }
        return response.getBody().toRunResponse();
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

    private String path(String template, String sessionId) {
        return template.replace("{sessionId}", sessionId);
    }

    private static RestTemplate restTemplate(DigitalTeamProperties.AgentCore properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
