-- F-05 BAS-27 组织分级管理员范围（Foundation 库迁移 V17；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

CREATE TABLE fdn_org_admin_scope (
    id                  VARCHAR(32) PRIMARY KEY,
    user_id             VARCHAR(64) NOT NULL,
    org_unit_id         VARCHAR(32) NOT NULL,
    include_descendants BOOLEAN NOT NULL DEFAULT TRUE,
    grantable_roles     TEXT,                                  -- JSON 数组；空表示不限
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, org_unit_id));
