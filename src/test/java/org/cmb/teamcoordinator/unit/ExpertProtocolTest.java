package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import org.cmb.teamcoordinator.expertprotocol.ExpertMessage;
import org.junit.jupiter.api.Test;

class ExpertProtocolTest {

    @Test
    void deserializesAndValidatesAllEightMessageTypes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        String common = "\"request_id\":\"request-1\",\"event_id\":\"event-1\",\"sequence\":1";
        for (String json : Arrays.asList(
                "{\"message_type\":\"EXPERT_TASK_REQUEST\"," + common
                        + ",\"objective\":\"analyze\",\"expert_id\":\"expert-analysis\"}",
                "{\"message_type\":\"EXPERT_TASK_ACCEPTED\"," + common
                        + ",\"session_id\":\"session-1\"}",
                "{\"message_type\":\"EXPERT_TASK_PROGRESS\"," + common
                        + ",\"message\":\"running\",\"percent\":50}",
                "{\"message_type\":\"EXPERT_TASK_RESULT\"," + common
                        + ",\"result\":{\"text\":\"done\"}}",
                "{\"message_type\":\"EXPERT_TASK_FAILED\"," + common
                        + ",\"error_code\":\"FAILED\",\"message\":\"failed\"}",
                "{\"message_type\":\"EXPERT_TASK_CANCEL\"," + common
                        + ",\"reason\":\"user request\"}",
                "{\"message_type\":\"EXPERT_HUMAN_INPUT_REQUIRED\"," + common
                        + ",\"question\":\"Which file?\"}",
                "{\"message_type\":\"EXPERT_TASK_RESUME\"," + common
                        + ",\"human_response\":\"Use file A\"}")) {
            ExpertMessage message = mapper.readValue(json, ExpertMessage.class);
            Set<ConstraintViolation<ExpertMessage>> violations = validator.validate(message);
            assertTrue(violations.isEmpty(), violations.toString());
            assertEquals("request-1", message.getRequestId());
        }
    }
}
