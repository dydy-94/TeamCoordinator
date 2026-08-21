package org.cmb.application.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DecisionSchemaValidator {

    private final JsonSchema schema;

    public DecisionSchemaValidator() {
        InputStream input = DecisionSchemaValidator.class.getResourceAsStream(
                "/coordinator/task-intent-schema-v1.json");
        if (input == null) {
            throw new IllegalStateException("Task intent JSON Schema is missing.");
        }
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(input);
    }

    public void validate(JsonNode output) {
        Set<ValidationMessage> errors = schema.validate(output);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Intent output failed JSON Schema: " + errors);
        }
    }
}
