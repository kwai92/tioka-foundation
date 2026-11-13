-- F-06 BAS-07 授权快照（Foundation 库迁移 V6；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

-- 授权快照：一个人员在一个应用里一份快照（UNIQUE(person_id, app_code)）
-- body 存快照内容 JSON（有效角色、权限码、可见菜单树、部门范围、各维度取值、人员属性）；
-- version 按 (person_id, app_code) 单调递增；变化即发 fdn.authz.changed（经 outbox）。
CREATE TABLE fdn_authz_snapshot (
    id          VARCHAR(32) PRIMARY KEY,
    person_id   VARCHAR(64) NOT NULL,
    app_code    VARCHAR(64) NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    body        JSONB,
    computed_at TIMESTAMPTZ,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (person_id, app_code));
-- 按应用取「已有快照的人员」与增量对账（latest 变更）
CREATE INDEX idx_snapshot_app_changed ON fdn_authz_snapshot (app_code, update_at);
