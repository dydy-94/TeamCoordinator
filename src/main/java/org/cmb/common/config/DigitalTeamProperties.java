package org.cmb.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "digital-team")
public class DigitalTeamProperties {

    private final AgentCore agentCore = new AgentCore();
    private final Storage storage = new Storage();
    private final Rollout rollout = new Rollout();
    private final Execution execution = new Execution();
    private final Events events = new Events();
    private final Platform platform = new Platform();

    public AgentCore getAgentCore() {
        return agentCore;
    }

    public Execution getExecution() {
        return execution;
    }

    public Events getEvents() {
        return events;
    }

    public Storage getStorage() {
        return storage;
    }

    public Rollout getRollout() {
        return rollout;
    }

    public Platform getPlatform() { return platform; }

    public static class AgentCore {
        private boolean mockEnabled = true;
        private String baseUrl;
        private String submitPath = "/{agentId}/chat";
        private String statusPath = "/{agentId}/sessions/{sessionId}";
        private String streamPath = "/{agentId}/sessions/{sessionId}/stream";
        private String cancelPath = "/{agentId}/sessions/{sessionId}/cancel";
        private String resumePath = "/{agentId}/sessions/{sessionId}/resume";
        private String coordinatorAgentId = "coordinator";
        private String sessionHeader = "X-Session-Id";
        private String authHeader = "Authorization";
        private String authValue;
        private String artifactToolToken;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 60000;
        /** Mock 事件持久化到文件（开发用）：重启后历史可重放。 */
        private boolean mockPersistEvents = false;
        private String mockEventsFile = ".mock-agentcore-sessions.json";

        public boolean isMockEnabled() {
            return mockEnabled;
        }

        public void setMockEnabled(boolean mockEnabled) {
            this.mockEnabled = mockEnabled;
        }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getSubmitPath() { return submitPath; }
        public void setSubmitPath(String submitPath) { this.submitPath = submitPath; }
        public String getStatusPath() { return statusPath; }
        public void setStatusPath(String statusPath) { this.statusPath = statusPath; }
        public String getStreamPath() { return streamPath; }
        public void setStreamPath(String streamPath) { this.streamPath = streamPath; }
        public String getCancelPath() { return cancelPath; }
        public void setCancelPath(String cancelPath) { this.cancelPath = cancelPath; }
        public String getResumePath() { return resumePath; }
        public void setResumePath(String resumePath) { this.resumePath = resumePath; }
        public String getCoordinatorAgentId() { return coordinatorAgentId; }
        public void setCoordinatorAgentId(String value) { this.coordinatorAgentId = value; }
        public String getSessionHeader() { return sessionHeader; }
        public void setSessionHeader(String value) { this.sessionHeader = value; }
        public String getAuthHeader() { return authHeader; }
        public void setAuthHeader(String authHeader) { this.authHeader = authHeader; }
        public String getAuthValue() { return authValue; }
        public void setAuthValue(String authValue) { this.authValue = authValue; }
        public String getArtifactToolToken() { return artifactToolToken; }
        public void setArtifactToolToken(String value) { this.artifactToolToken = value; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int value) { this.connectTimeoutMs = value; }
        public boolean isMockPersistEvents() { return mockPersistEvents; }
        public void setMockPersistEvents(boolean value) { this.mockPersistEvents = value; }
        public String getMockEventsFile() { return mockEventsFile; }
        public void setMockEventsFile(String value) { this.mockEventsFile = value; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int value) { this.readTimeoutMs = value; }
    }

    public static class Storage {
        private String type = "minio";
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "digital-team";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    public static class Rollout {
        private boolean enabled = true;
        private boolean emergencyStop;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public boolean isEmergencyStop() { return emergencyStop; }
        public void setEmergencyStop(boolean value) { this.emergencyStop = value; }
    }

    /** 平台管理员(租户管理引导角色):外部 userId 列表。 */
    public static class Platform {
        private List<String> adminUsers = new ArrayList<>();

        public List<String> getAdminUsers() { return adminUsers; }
        public void setAdminUsers(List<String> value) { this.adminUsers = value; }
    }



    public static class Events {
        /** SSE heartbeat interval: comment frames keep idle connections alive. */
        private long heartbeatIntervalMs = 60000;
        /** Close an SSE connection after this many minutes without user messages. */
        private int inactivityTimeoutMin = 30;

        public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(long value) { this.heartbeatIntervalMs = value; }
        public int getInactivityTimeoutMin() { return inactivityTimeoutMin; }
        public void setInactivityTimeoutMin(int value) { this.inactivityTimeoutMin = value; }
    }

    public static class Execution {
        /**
         * Consecutive AgentCore failures (stream errors or an unreachable run)
         * tolerated before a task is failed. Each poll tick counts as one.
         */
        private int agentcoreFailureThreshold = 60;

        public int getAgentcoreFailureThreshold() { return agentcoreFailureThreshold; }
        public void setAgentcoreFailureThreshold(int value) { this.agentcoreFailureThreshold = value; }
    }
}
