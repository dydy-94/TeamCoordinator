-- 测试租户种子(Flyway 之后执行)。覆盖全部集成测试使用的
-- (tenant, user) 对;outsider / not-a-member 等「陌生人」也必须是
-- 租户成员,以保留既有「项目不可见 → 404」语义;真正的非成员
-- 403 场景由 TenantAccessIntegrationTest 用全新 userId 覆盖。
INSERT IGNORE INTO digital_team_tenant
    (business_id, name, description, owner_user_id, status, created_by) VALUES
    ('tenant-cli', 'tenant-cli', '', 'cli-owner', 'ACTIVE', 'test'),
    ('tenant-fault', 'tenant-fault', '', 'fault-owner', 'ACTIVE', 'test'),
    ('tenant-multi', 'tenant-multi', '', 'multi-owner', 'ACTIVE', 'test'),
    ('tenant-execution', 'tenant-execution', '', 'execution-owner', 'ACTIVE', 'test'),
    ('deletion-tenant', 'deletion-tenant', '', 'deletion-owner', 'ACTIVE', 'test'),
    ('tenant-human', 'tenant-human', '', 'human-owner', 'ACTIVE', 'test'),
    ('tenant-message', 'tenant-message', '', 'message-owner', 'ACTIVE', 'test'),
    ('tenant-a', 'tenant-a', '', 'owner-a', 'ACTIVE', 'test'),
    ('tenant-b', 'tenant-b', '', 'owner-a', 'ACTIVE', 'test'),
    ('tenant-artifact', 'tenant-artifact', '', 'artifact-owner', 'ACTIVE', 'test'),
    ('tenant-prompt', 'tenant-prompt', '', 'prompt-admin', 'ACTIVE', 'test');

INSERT IGNORE INTO digital_team_tenant_user (tenant_id, user_id, role) VALUES
    ('tenant-cli', 'cli-owner', 'TENANT_ADMIN'),
    ('tenant-fault', 'fault-owner', 'TENANT_ADMIN'),
    ('tenant-multi', 'multi-owner', 'TENANT_ADMIN'),
    ('tenant-execution', 'execution-owner', 'TENANT_ADMIN'),
    ('deletion-tenant', 'deletion-owner', 'TENANT_ADMIN'),
    ('tenant-human', 'human-owner', 'TENANT_ADMIN'),
    ('tenant-human', 'ordinary-member', 'MEMBER'),
    ('tenant-message', 'message-owner', 'TENANT_ADMIN'),
    ('tenant-message', 'outsider', 'MEMBER'),
    ('tenant-a', 'owner-a', 'TENANT_ADMIN'),
    ('tenant-a', 'member-a', 'MEMBER'),
    ('tenant-a', 'viewer-a', 'MEMBER'),
    ('tenant-a', 'not-a-member', 'MEMBER'),
    ('tenant-b', 'owner-a', 'TENANT_ADMIN'),
    ('tenant-artifact', 'artifact-owner', 'TENANT_ADMIN'),
    ('tenant-artifact', 'outsider', 'MEMBER'),
    ('tenant-prompt', 'prompt-admin', 'TENANT_ADMIN'),
    ('tenant-prompt', 'ordinary-user', 'MEMBER');
