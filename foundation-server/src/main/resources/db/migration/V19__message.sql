-- F-09 BAS-16 消息中心 —— 消息主体（Foundation 库迁移 V19；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

CREATE TABLE fdn_message (
    id            VARCHAR(32)   PRIMARY KEY,                  -- ULID，前缀 msg_
    app_code      VARCHAR(64)   NOT NULL,                     -- 发送方应用
    kind          VARCHAR(16)   NOT NULL,                     -- INBOX / TODO / NOTICE / ANNOUNCEMENT
    title         VARCHAR(512)  NOT NULL,
    body          TEXT,
    template_code VARCHAR(64),
    payload       JSONB,
    link          VARCHAR(1024),                              -- 深链：只带业务对象 ID（ADR-029 第 57 条）
    priority      VARCHAR(8)    NOT NULL DEFAULT 'NORMAL',    -- LOW / NORMAL / HIGH
    sender_id     VARCHAR(64),
    sender_name   VARCHAR(255),
    channels      VARCHAR(128)  NOT NULL DEFAULT 'INBOX',     -- INBOX,MAIL,FEISHU
    dedupe_key    VARCHAR(128),                               -- 同 (app_code, dedupe_key) 幂等
    data_label    VARCHAR(16)   NOT NULL DEFAULT 'INTERNAL',  -- ADR-040
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (app_code, dedupe_key),
    CONSTRAINT ck_message_kind CHECK (kind IN ('INBOX','TODO','NOTICE','ANNOUNCEMENT')),
    CONSTRAINT ck_message_priority CHECK (priority IN ('LOW','NORMAL','HIGH')),
    CONSTRAINT ck_message_data_label CHECK (data_label IN ('PUBLIC','INTERNAL','CONFIDENTIAL','SECRET')));

CREATE INDEX idx_message_app_kind ON fdn_message (app_code, kind);
