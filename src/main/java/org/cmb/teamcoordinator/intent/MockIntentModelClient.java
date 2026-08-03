package org.cmb.teamcoordinator.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MockIntentModelClient implements IntentModelClient {

    private final ObjectMapper objectMapper;

    public MockIntentModelClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String modelName() {
        return "mock-intent-v1";
    }

    @Override
    public String analyze(String prompt, IntentAnalysisContext context) {
        if (context.getText().startsWith("__invalid_once__")
                || context.getText().startsWith("__always_invalid__")) {
            return "{invalid";
        }
        return write(classify(context));
    }

    @Override
    public String repair(String prompt, String invalidOutput, IntentAnalysisContext context) {
        if (context.getText().startsWith("__always_invalid__")) {
            return "{still-invalid";
        }
        return write(classify(context));
    }

    public CoordinatorDecision classify(IntentAnalysisContext context) {
        String text = context.getText().trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        if (isBlockingAmbiguity(normalized)) {
            CoordinatorDecision decision = new CoordinatorDecision();
            decision.setDecisionType(DecisionType.ASK_HUMAN);
            decision.setQuestion("请补充要处理的具体对象、目标或期望输出。");
            return decision;
        }
        if (isDirectQuestion(normalized)) {
            CoordinatorDecision decision = new CoordinatorDecision();
            decision.setDecisionType(DecisionType.ANSWER);
            decision.setAnswer("根据当前项目上下文，" + text);
            return decision;
        }

        CoordinatorDecision decision = new CoordinatorDecision();
        decision.setDecisionType(DecisionType.CREATE_PLAN);
        TaskIntent intent = new TaskIntent();
        intent.setIntent(inferIntent(normalized));
        intent.setObjective(text);
        intent.setExpectedOutputs(inferOutputs(normalized));
        intent.setConstraints(Collections.singletonList("使用项目当前可用专家和已有上下文"));
        intent.setRequiredCapabilities(inferCapabilities(normalized));
        intent.setInputRefs(new ArrayList<>(context.getAttachmentRefs()));
        intent.setMissingInformation(Collections.emptyList());
        intent.setRiskLevel(inferRisk(normalized));
        intent.setExecutionMode(isMultiExpert(normalized)
                ? ExecutionMode.MULTI_EXPERT : ExecutionMode.SINGLE_EXPERT);
        decision.setTaskIntent(intent);
        return decision;
    }

    private boolean isBlockingAmbiguity(String text) {
        return Arrays.asList("处理一下", "帮我看看", "做一下", "继续处理", "弄一下")
                .contains(text)
                || text.contains("目标不确定")
                || text.contains("不知道要什么");
    }

    private boolean isDirectQuestion(String text) {
        return text.startsWith("什么是")
                || text.startsWith("解释")
                || text.startsWith("介绍一下")
                || text.startsWith("what is")
                || text.startsWith("explain")
                || text.endsWith("是什么意思");
    }

    private boolean isMultiExpert(String text) {
        return (text.contains("分析") && (text.contains("报告") || text.contains("撰写")))
                || text.contains("多专家")
                || text.contains("并生成")
                || text.contains("and write");
    }

    private String inferIntent(String text) {
        if (text.contains("风险") || text.contains("分析")) {
            return "ANALYZE";
        }
        if (text.contains("报告") || text.contains("撰写") || text.contains("write")) {
            return "CREATE_CONTENT";
        }
        return "EXECUTE_TASK";
    }

    private List<String> inferOutputs(String text) {
        if (text.contains("报告")) {
            return Collections.singletonList("分析报告");
        }
        return Collections.singletonList("任务结果");
    }

    private List<String> inferCapabilities(String text) {
        List<String> capabilities = new ArrayList<>();
        if (text.contains("分析") || text.contains("风险")) {
            capabilities.add("analysis");
        }
        if (text.contains("报告") || text.contains("撰写") || text.contains("write")) {
            capabilities.add("writing");
        }
        if (capabilities.isEmpty()) {
            capabilities.add("analysis");
        }
        return capabilities;
    }

    private RiskLevel inferRisk(String text) {
        if (text.contains("删除") || text.contains("发布") || text.contains("生产")) {
            return RiskLevel.HIGH;
        }
        return text.contains("风险") ? RiskLevel.MEDIUM : RiskLevel.LOW;
    }

    private String write(CoordinatorDecision decision) {
        try {
            return objectMapper.writeValueAsString(decision);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize mock intent output.", ex);
        }
    }
}
