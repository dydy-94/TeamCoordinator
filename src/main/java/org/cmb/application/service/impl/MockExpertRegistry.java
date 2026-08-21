package org.cmb.application.service.impl;
import org.cmb.application.service.ExpertRegistry;
import org.cmb.application.domain.ExpertDescriptor;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "digital-team.agent-core", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockExpertRegistry implements ExpertRegistry {

    @Override
    public List<ExpertDescriptor> listExperts() {
        return Arrays.asList(
                new ExpertDescriptor("expert-analysis", "Analysis Expert",
                        Arrays.asList("analysis", "risk_review"),
                        "擅长需求分析、风险评估、代码审查"),
                new ExpertDescriptor("expert-writing", "Writing Expert",
                        Arrays.asList("writing", "report"),
                        "擅长撰写报告、文档、技术方案"),
                new ExpertDescriptor("expert-file", "File Expert",
                        Arrays.asList("file_processing", "artifact"),
                        "擅长文件处理、产物管理"),
                new ExpertDescriptor("expert-ui", "UI Design Expert",
                        Arrays.asList("ui_design", "frontend"),
                        "擅长界面设计、前端开发、组件库使用"),
                new ExpertDescriptor("expert-backend", "Backend Expert",
                        Arrays.asList("backend", "api_design"),
                        "擅长后端开发、API设计、数据库优化")
        );
    }
}
