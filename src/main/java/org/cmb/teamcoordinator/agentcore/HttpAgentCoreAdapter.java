package org.cmb.teamcoordinator.agentcore;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    public AgentRunResponse submitRun(AgentRunRequest request) {
        ResponseEntity<AgentRunResponse> response = restTemplate.exchange(
                uri(properties.getSubmitPath()),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders(request.getBusinessSessionId())),
                AgentRunResponse.class);
        return response.getBody();
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
        return parseSse(response.getBody());
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
            ResponseEntity<AgentRunEvent> response = restTemplate.exchange(
                    uri(path(properties.getCancelPath(), sessionId)),
                    HttpMethod.POST,
                    new HttpEntity<Void>(jsonHeaders(businessSessionId)),
                    AgentRunEvent.class);
            return response.getBody();
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
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("human_response", humanResponse);
        body.put("idempotency_key", idempotencyKey);
        ResponseEntity<AgentRunResponse> response = restTemplate.exchange(
                uri(path(properties.getResumePath(), sessionId)),
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(businessSessionId)),
                AgentRunResponse.class);
        return response.getBody();
    }

    private List<AgentRunEvent> parseSse(String body) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentRunEvent> result = new ArrayList<>();
        String normalized = body.replace("\r\n", "\n");
        for (String block : normalized.split("\\n\\n")) {
            String id = null;
            String type = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\\n")) {
                if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("event:")) {
                    type = line.substring(6).trim();
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
                AgentRunEvent event =
                        objectMapper.readValue(data.toString(), AgentRunEvent.class);
                if (event.getType() == null) {
                    event.setType(type);
                }
                if (event.getSequence() == 0 && id != null) {
                    event.setSequence(Long.parseLong(id));
                }
                if (event.getEventId() == null) {
                    event.setEventId(event.getSessionId() + ":" + event.getSequence());
                }
                result.add(event);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not parse AgentCore SSE event.", ex);
            }
        }
        return result;
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
