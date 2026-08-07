CREATE TABLE skill (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    prompt TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project_skill (
    project_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, skill_id),
    CONSTRAINT fk_project_skill_project FOREIGN KEY (project_id) REFERENCES project (business_id),
    CONSTRAINT fk_project_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_project_skill_tenant ON project_skill (tenant_id, project_id);

-- Seed some built-in skills for the platform
INSERT INTO skill (business_id, name, description, prompt) VALUES
('skill-code-review', '代码审查', '自动审查代码质量、安全漏洞和最佳实践合规性',
 'You are a code review expert. Analyze the provided code for bugs, security vulnerabilities, performance issues, and best practice violations. Provide specific, actionable feedback.'),
('skill-pdf-gen', 'PDF生成', '根据模板和数据生成格式化的PDF文档',
 'You are a PDF generation expert. Create well-formatted PDF documents from provided templates and data. Ensure proper layout, fonts, and structure.'),
('skill-data-analysis', '数据分析', '对结构化数据执行统计分析并生成可视化图表',
 'You are a data analysis expert. Perform statistical analysis on structured data, identify trends and patterns, and generate clear visualizations.'),
('skill-ui-design', 'UI设计', '使用组件库和设计系统创建用户界面原型',
 'You are a UI design expert. Create user interface prototypes using the design system component library. Follow accessibility guidelines and responsive design principles.'),
('skill-api-doc', 'API文档生成', '根据代码注解和接口定义自动生成API文档',
 'You are an API documentation expert. Generate comprehensive API documentation from code annotations and interface definitions. Include request/response examples and error codes.');
