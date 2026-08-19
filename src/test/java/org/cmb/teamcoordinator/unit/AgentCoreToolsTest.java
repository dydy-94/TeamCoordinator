package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.cmb.teamcoordinator.agentcore.AgentCoreTools;
import org.junit.jupiter.api.Test;

class AgentCoreToolsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void toolDefinitionsCarryExpectedNamesAndSchemas() throws Exception {
        JsonNode decision = loadTool("submit-coordinator-decision.json");
        JsonNode plan = loadTool("submit-coordinator-plan.json");
        JsonNode verdict = loadTool("submit-review-verdict.json");

        assertEquals(AgentCoreTools.SUBMIT_COORDINATOR_DECISION,
                decision.get("name").asText());
        assertEquals(AgentCoreTools.SUBMIT_COORDINATOR_PLAN,
                plan.get("name").asText());
        assertEquals(AgentCoreTools.SUBMIT_REVIEW_VERDICT,
                verdict.get("name").asText());
        assertTrue(verdict.get("parameters").get("required")
                .toString().contains("consistent"));
    }

    @Test
    void toolParametersMatchRuntimeSchemas() throws Exception {
        assertParametersMatch(
                "submit-coordinator-decision.json",
                "/coordinator/task-intent-schema-v1.json");
        assertParametersMatch(
                "submit-coordinator-plan.json",
                "/coordinator/plan-schema-v1.json");
    }

    private void assertParametersMatch(String toolFile, String schemaResource)
            throws Exception {
        JsonNode tool = loadTool(toolFile);
        JsonNode schema = MAPPER.readTree(
                AgentCoreToolsTest.class.getResourceAsStream(schemaResource));
        assertEquals(schema, tool.get("parameters"),
                "Tool parameters drifted from the runtime schema: " + toolFile);
    }

    private JsonNode loadTool(String file) throws Exception {
        InputStream input = AgentCoreToolsTest.class.getResourceAsStream(
                "/agentcore-tools/" + file);
        if (input == null) {
            throw new IllegalStateException("Tool definition missing: " + file);
        }
        try (InputStream in = input) {
            return MAPPER.readTree(in);
        }
    }
}
