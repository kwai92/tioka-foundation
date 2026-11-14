-- F-04 BAS-14 交接待办（Foundation 库迁移 V14；ADR-029 第 60 条：无 tenant_id）
-- 人员离职时，其作为 owner_user_id 的服务账号进入交接待办队列，由管理员指定新负责人；
-- 同一服务账号只保留一条待移交（status=PENDING）待办。
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

CREATE TABLE fdn_handover_todo (
    id                  VARCHAR(32)  PRIMARY KEY,                          -- hdo_{ulid}
    owner_person_id     VARCHAR(32)  NOT NULL,                             -- 原负责人（离职人员）
    account_id          VARCHAR(32)  NOT NULL,                             -- 服务账号主体（fdn_account.id）
    service_account_id  VARCHAR(32)  NOT NULL,                             -- BAS-04 绑定（fdn_service_account.id）
    app_code            VARCHAR(64)  NOT NULL,                             -- 所属应用编码
    name                VARCHAR(128) NOT NULL,                             -- 服务账号名称
    permission_codes    TEXT,                                              -- 权限码白名单（逗号分隔）
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',            -- PENDING / HANDED_OVER
    new_owner_person_id VARCHAR(32),                                       -- 指定新负责人
    handled_at          TIMESTAMPTZ,                                       -- 移交时间
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_handover_status CHECK (status IN ('PENDING','HANDED_OVER')));

-- 同一服务账号只保留一条待移交待办（已移交后可再次入队）
CREATE UNIQUE INDEX uk_handover_pending ON fdn_handover_todo (account_id)
    WHERE status = 'PENDING' AND deleted = FALSE;
CREATE INDEX idx_handover_owner ON fdn_handover_todo (owner_person_id, status) WHERE deleted = FALSE;
