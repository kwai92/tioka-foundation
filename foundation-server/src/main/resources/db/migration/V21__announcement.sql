-- F-09 BAS-16 消息中心 —— 公告头（Foundation 库迁移 V21）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

CREATE TABLE fdn_announcement (
    id           VARCHAR(32)  PRIMARY KEY,                     -- ULID，前缀 ann_
    title        VARCHAR(512) NOT NULL,
    content      TEXT         NOT NULL,
    scope_type   VARCHAR(16)  NOT NULL,                        -- GLOBAL / ORG / ROLE
    scope_id     VARCHAR(64),                                  -- ORG / ROLE 时的目标 ID
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',        -- DRAFT / PUBLISHED / OFFLINE
    publisher_id VARCHAR(64)  NOT NULL,
    data_label   VARCHAR(16)  NOT NULL DEFAULT 'INTERNAL',
    publish_at   TIMESTAMPTZ,
    expire_at    TIMESTAMPTZ,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_announcement_scope CHECK (scope_type IN ('GLOBAL','ORG','ROLE')),
    CONSTRAINT ck_announcement_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE')),
    CONSTRAINT ck_announcement_data_label CHECK (data_label IN ('PUBLIC','INTERNAL','CONFIDENTIAL','SECRET')));

CREATE INDEX idx_announcement_status ON fdn_announcement (status, publish_at DESC);
