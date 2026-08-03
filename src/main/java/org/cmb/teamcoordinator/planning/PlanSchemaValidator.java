package org.cmb.teamcoordinator.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PlanSchemaValidator {

    private final JsonSchema schema;

    public PlanSchemaValidator() {
        InputStream input = PlanSchemaValidator.class.getResourceAsStream(
                "/coordinator/plan-schema-v1.json");
        if (input == null) {
            throw new IllegalStateException("Plan JSON Schema is missing.");
        }
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(input);
    }

    public void validate(JsonNode output) {
        Set<ValidationMessage> errors = schema.validate(output);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Plan output failed JSON Schema: " + errors);
        }
    }
}
