package org.cmb.presentation.controller;

import java.util.List;
import org.cmb.application.domain.Skill;
import org.cmb.infrastructure.persistent.SkillRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-level skill catalog. Lists all built-in skills available from
 * the platform AgentCore. For project-scoped skill management, see
 * {@link ProjectController}.
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillRepository skillRepository;

    public SkillController(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * List all skills in the platform catalog.
     */
    @GetMapping
    public List<Skill> list() {
        return skillRepository.listAll();
    }
}
