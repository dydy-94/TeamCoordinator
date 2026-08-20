package org.cmb.teamcoordinator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.cmb.application.service.DecisionSchemaValidator;
import org.cmb.common.enums.DecisionType;
import org.cmb.application.domain.IntentAnalysisContext;
import org.cmb.infrastructure.remoteaccess.MockIntentModelClient;
import org.junit.jupiter.api.Test;

class IntentEvaluationTest {

    @Test
    void fixedEvaluationSetMeetsDecisionThresholdsAndSchemaContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MockIntentModelClient model = new MockIntentModelClient(objectMapper);
        DecisionSchemaValidator schemaValidator = new DecisionSchemaValidator();
        List<String> samples = samples();
        int correct = 0;
        int askTotal = 0;
        int askCorrect = 0;
        int noAskTotal = 0;
        int unnecessaryAsk = 0;

        for (String sample : samples) {
            String[] columns = sample.split("\\|", 3);
            IntentAnalysisContext context = new IntentAnalysisContext();
            context.setText(columns[2]);
            JsonNode output = objectMapper.readTree(model.analyze("prompt-v1", context));
            schemaValidator.validate(output);
            DecisionType actual = DecisionType.valueOf(output.get("decision_type").asText());
            DecisionType expected = DecisionType.valueOf(columns[1]);
            if (actual == expected) {
                correct++;
            }
            if ("ASK".equals(columns[0])) {
                askTotal++;
                if (actual == DecisionType.ASK_HUMAN) {
                    askCorrect++;
                }
            }
            if ("NO_ASK".equals(columns[0])) {
                noAskTotal++;
                if (actual == DecisionType.ASK_HUMAN) {
                    unnecessaryAsk++;
                }
            }
        }

        assertEquals(60, samples.size());
        assertTrue(correct / 60.0 >= 0.90);
        assertTrue(askCorrect / (double) askTotal >= 0.95);
        assertTrue(unnecessaryAsk / (double) noAskTotal <= 0.10);
    }

    private List<String> samples() throws Exception {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/intent-evaluation-v1.txt"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    result.add(line);
                }
            }
        }
        return result;
    }
}
