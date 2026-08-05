package org.cmb.teamcoordinator.api;

import java.util.Arrays;
import java.util.List;
import org.cmb.teamcoordinator.agentcore.ExpertInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experts")
public class ExpertsController {

    private static final List<ExpertInfo> EXPERTS = Arrays.asList(
            new ExpertInfo("expert-analysis", "Analysis Expert",
                    "擅长需求分析、风险评估、代码审查",
                    "You are an analysis expert. Break down complex problems, "
                            + "identify risks, and provide structured analysis reports."),
            new ExpertInfo("expert-writing", "Writing Expert",
                    "擅长撰写报告、文档、技术方案",
                    "You are a writing expert. Produce clear, well-structured "
                            + "documents and reports based on provided analysis."),
            new ExpertInfo("expert-file", "File Expert",
                    "擅长文件处理、产物管理",
                    "You are a file expert. Handle file operations, artifact "
                            + "management, and data transformations."),
            new ExpertInfo("expert-ui", "UI Design Expert",
                    "擅长界面设计、前端开发、组件库使用",
                    "You are a UI design expert. Create beautiful, functional "
                            + "user interfaces using modern design principles."),
            new ExpertInfo("expert-backend", "Backend Expert",
                    "擅长后端开发、API设计、数据库优化",
                    "You are a backend expert. Design robust APIs, optimize "
                            + "database queries, and implement scalable services."));

    @GetMapping
    public List<ExpertInfo> list() {
        return EXPERTS;
    }
}
