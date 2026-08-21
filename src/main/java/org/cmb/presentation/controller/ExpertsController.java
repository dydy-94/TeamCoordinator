package org.cmb.presentation.controller;

import java.util.List;
import java.util.stream.Collectors;
import org.cmb.application.domain.ExpertDescriptor;
import org.cmb.application.service.ExpertRegistry;
import org.cmb.application.dto.ExpertInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experts")
public class ExpertsController {

    private final ExpertRegistry expertRegistry;

    public ExpertsController(ExpertRegistry expertRegistry) {
        this.expertRegistry = expertRegistry;
    }

    /** 专家目录唯一事实源是 ExpertRegistry（能力、描述、并发上限）。 */
    @GetMapping
    public List<ExpertInfo> list() {
        return expertRegistry.listExperts().stream()
                .map(this::toInfo)
                .collect(Collectors.toList());
    }

    private ExpertInfo toInfo(ExpertDescriptor expert) {
        return new ExpertInfo(
                expert.getExpertId(),
                expert.getDisplayName(),
                expert.getDescription(),
                expert.getCapabilities());
    }
}
