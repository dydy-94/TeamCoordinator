package org.cmb.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Guards the schema copies shipped with the companion CLI against the
 * runtime schemas used for server-side validation. The CLI validates
 * submissions locally with hand-rolled logic that mirrors these files;
 * any drift between the two copies must be intentional and synchronized.
 */
class CliSchemaConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void cliSchemasMatchRuntimeSchemas() throws Exception {
        assertSame(
                "/coordinator/task-intent-schema-v1.json",
                "src/main/cli/schemas/task-intent-schema-v1.json");
        assertSame(
                "/coordinator/plan-schema-v1.json",
                "src/main/cli/schemas/plan-schema-v1.json");
    }

    private void assertSame(String runtimeResource, String cliFile)
            throws Exception {
        InputStream runtime = CliSchemaConsistencyTest.class.getResourceAsStream(
                runtimeResource);
        if (runtime == null) {
            throw new IllegalStateException("Missing resource: " + runtimeResource);
        }
        JsonNode runtimeJson;
        try (InputStream in = runtime) {
            runtimeJson = MAPPER.readTree(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        File file = new File(cliFile);
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "Missing CLI schema file (run from project root): " + cliFile);
        }
        JsonNode cliJson = MAPPER.readTree(
                new String(java.nio.file.Files.readAllBytes(file.toPath()),
                        StandardCharsets.UTF_8));
        assertEquals(runtimeJson, cliJson,
                "CLI schema drifted from runtime schema: " + cliFile);
    }
}
