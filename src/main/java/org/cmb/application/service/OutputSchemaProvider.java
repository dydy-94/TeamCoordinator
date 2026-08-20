package org.cmb.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Loads the JSON Schemas that define the Coordinator's output contracts so
 * they can be injected into prompts at render time. Embedding the schema in
 * the prompt makes the output contract explicit for the agent instead of
 * relying on the agent-side configuration alone.
 */
@Component
public class OutputSchemaProvider {

    public static final String TASK_INTENT_SCHEMA = "/coordinator/task-intent-schema-v1.json";
    public static final String PLAN_SCHEMA = "/coordinator/plan-schema-v1.json";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String taskIntentSchema() {
        return load(TASK_INTENT_SCHEMA);
    }

    public String planSchema() {
        return load(PLAN_SCHEMA);
    }

    private String load(String resource) {
        return cache.computeIfAbsent(resource, key -> {
            InputStream input = OutputSchemaProvider.class.getResourceAsStream(key);
            if (input == null) {
                throw new IllegalStateException(
                        "Output schema resource is missing: " + key);
            }
            try (InputStream in = input) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException(
                        "Could not read output schema resource: " + key, ex);
            }
        });
    }
}
