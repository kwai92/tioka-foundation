-- F-05 BAS-27 功能开关（Foundation 库迁移 V16；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

CREATE TABLE fdn_feature_toggle (
    id         VARCHAR(32) PRIMARY KEY,
    scope_type VARCHAR(16) NOT NULL,                           -- SUBSYSTEM / MODULE / FUNCTION
    scope_id   VARCHAR(32) NOT NULL,
    enabled    BOOLEAN NOT NULL,
    reason     VARCHAR(512),
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (scope_type, scope_id),
    CONSTRAINT ck_toggle_scope_type CHECK (scope_type IN ('SUBSYSTEM','MODULE','FUNCTION')));
