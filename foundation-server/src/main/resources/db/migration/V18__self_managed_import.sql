-- F-05 BAS-27 自管子系统一次性导入（Foundation 库迁移 V18；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

-- 导入批次（自管改集中前的过渡）
CREATE TABLE fdn_self_managed_import (
    id           VARCHAR(32) PRIMARY KEY,
    subsystem_id VARCHAR(32) NOT NULL REFERENCES fdn_subsystem(id),
    status       VARCHAR(16) NOT NULL DEFAULT 'PREVIEW',       -- PREVIEW / APPLIED / DISCARDED
    summary      TEXT,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_self_import_status CHECK (status IN ('PREVIEW','APPLIED','DISCARDED')));

-- 导入明细项（角色 / 授权）
CREATE TABLE fdn_self_managed_import_item (
    id           VARCHAR(32) PRIMARY KEY,
    batch_id     VARCHAR(32) NOT NULL REFERENCES fdn_self_managed_import(id),
    kind         VARCHAR(16) NOT NULL,                         -- ROLE / ASSIGNMENT
    external_key VARCHAR(255) NOT NULL,
    payload      TEXT NOT NULL,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (batch_id, kind, external_key),
    CONSTRAINT ck_self_import_kind CHECK (kind IN ('ROLE','ASSIGNMENT')));
