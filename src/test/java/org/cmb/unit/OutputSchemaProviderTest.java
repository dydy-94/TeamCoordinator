package org.cmb.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cmb.application.service.OutputSchemaProvider;
import org.junit.jupiter.api.Test;

class OutputSchemaProviderTest {

    @Test
    void loadsBothOutputSchemasAsJson() throws Exception {
        OutputSchemaProvider provider = new OutputSchemaProvider();
        ObjectMapper mapper = new ObjectMapper();

        String intent = provider.taskIntentSchema();
        String plan = provider.planSchema();

        assertFalse(intent.trim().isEmpty());
        assertFalse(plan.trim().isEmpty());
        mapper.readTree(intent);
        mapper.readTree(plan);
    }

    @Test
    void taskIntentSchemaConstrainsDecisionType() throws Exception {
        OutputSchemaProvider provider = new OutputSchemaProvider();
        String intent = provider.taskIntentSchema();

        assertTrue(intent.contains("ANSWER"));
        assertTrue(intent.contains("ASK_HUMAN"));
        assertTrue(intent.contains("CREATE_PLAN"));
    }

    @Test
    void schemasAreCached() {
        OutputSchemaProvider provider = new OutputSchemaProvider();

        assertTrue(provider.taskIntentSchema() == provider.taskIntentSchema());
        assertTrue(provider.planSchema() == provider.planSchema());
    }
}
