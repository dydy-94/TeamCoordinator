package org.cmb.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.cmb.common.config.DigitalTeamProperties;
import org.cmb.infrastructure.persistent.PromptRepository;
import org.cmb.application.service.PromptService;
import org.cmb.application.dto.PromptTemplateView;
import org.cmb.application.dto.RenderedPrompt;
import org.junit.jupiter.api.Test;

class PromptServiceTest {

    @Test
    void replacesExtraVariablesAndContextJson() {
        PromptTemplateView template = template(
                "<schema>{{output_schema}}</schema>\n<context>{{context_json}}</context>");
        PromptService service = service(template);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("output_schema", "{\"type\":\"object\"}");

        RenderedPrompt rendered = service.render(
                "coordinator.execution", context("hi"), variables,
                "tenant", "project", "conversation", "invocation", "coordinator");

        assertTrue(rendered.getContent().contains(
                "<schema>{\"type\":\"object\"}</schema>"));
        assertTrue(rendered.getContent().contains(
                "<context>{\"text\":\"hi\"}</context>"));
    }

    @Test
    void ignoresExtraVariablesNotPresentInTemplate() {
        PromptTemplateView template = template("plain {{context_json}}");
        PromptService service = service(template);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("output_schema", "{\"type\":\"object\"}");

        RenderedPrompt rendered = service.render(
                "coordinator.execution", context("hi"), variables,
                "tenant", "project", "conversation", "invocation", "coordinator");

        assertEquals("plain {\"text\":\"hi\"}", rendered.getContent());
    }

    @Test
    void throwsWhenTemplateHasUnresolvedVariables() {
        PromptTemplateView template = template("{{context_json}} {{unknown}}");
        PromptService service = service(template);

        assertThrows(IllegalStateException.class, () -> service.render(
                "coordinator.execution", "ctx", null,
                "tenant", "project", "conversation", "invocation", "coordinator"));
    }

    private PromptService service(PromptTemplateView template) {
        return new PromptService(
                new StubPromptRepository(template),
                new ObjectMapper(),
                new DigitalTeamProperties());
    }

    private Map<String, Object> context(String text) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("text", text);
        return context;
    }

    private PromptTemplateView template(String content) {
        PromptTemplateView view = new PromptTemplateView();
        view.setId("prompt-test");
        view.setPromptKey("coordinator.execution");
        view.setScene("COORDINATOR_EXECUTION");
        view.setVersion(2);
        view.setTemplateContent(content);
        return view;
    }

    private static class StubPromptRepository extends PromptRepository {

        private final PromptTemplateView template;

        StubPromptRepository(PromptTemplateView template) {
            super(null, null);
            this.template = template;
        }

        @Override
        public PromptTemplateView findPublished(String promptKey) {
            return template;
        }

        @Override
        public void audit(
                String tenantId, String projectId, String conversationId,
                String invocationId, String agentId, PromptTemplateView template,
                String renderedPrompt, String variablesSnapshot) {
            // no-op
        }
    }
}
