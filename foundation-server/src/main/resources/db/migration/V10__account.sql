-- F-04 BAS-03 账号（Foundation 库迁移 V10；ADR-029 第 60 条：无 tenant_id）
-- 账号是「人用来登录的凭证载体」，由 Foundation 推送到 Keycloak 并回写其用户 ID；Foundation 不保存密码。
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

CREATE TABLE fdn_account (
    id               VARCHAR(32)  PRIMARY KEY,                      -- acc_{ulid}
    login_name       VARCHAR(128) NOT NULL,                         -- 登录名，全局唯一，不回收
    person_id        VARCHAR(32),                                   -- 个人账号必填；应急管理员与服务账号为 NULL
    keycloak_user_id VARCHAR(64),                                   -- 推送 Keycloak 后回写，全局唯一；待发时为 NULL（先落库再推送）
    status           VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',        -- NORMAL / LOCKED / DISABLED
    type             VARCHAR(16)  NOT NULL DEFAULT 'PERSONAL',      -- PERSONAL / EMERGENCY_ADMIN / SERVICE
    last_login_at    TIMESTAMPTZ,                                   -- 由 BAS-10 登录事件采集回填
    version          BIGINT       NOT NULL DEFAULT 0,               -- 事件版本
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_account_status CHECK (status IN ('NORMAL','LOCKED','DISABLED')),
    CONSTRAINT ck_account_type   CHECK (type IN ('PERSONAL','EMERGENCY_ADMIN','SERVICE')),
    -- 仅个人账号关联人员；应急管理员与服务账号不关联人员（F-04 §1）
    CONSTRAINT ck_account_emergency_no_person CHECK (type = 'PERSONAL' OR person_id IS NULL));
CREATE UNIQUE INDEX uk_account_login_name   ON fdn_account (login_name);
CREATE UNIQUE INDEX uk_account_keycloak_uid ON fdn_account (keycloak_user_id);
-- 一个人员最多一个个人账号（S15 第 8.2 节）
CREATE UNIQUE INDEX uk_account_person_personal ON fdn_account (person_id)
    WHERE type = 'PERSONAL' AND deleted = FALSE;
CREATE INDEX idx_account_person ON fdn_account (person_id) WHERE deleted = FALSE;
