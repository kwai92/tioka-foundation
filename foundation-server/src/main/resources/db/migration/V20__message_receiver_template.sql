-- F-09 BAS-16 消息中心 —— 收件人 + 渠道模板（Foundation 库迁移 V20）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

-- 收件人：决定「已读 / 未读」与投递状态
CREATE TABLE fdn_message_receiver (
    id             VARCHAR(32) PRIMARY KEY,                   -- ULID，前缀 mrc_
    message_id     VARCHAR(32) NOT NULL REFERENCES fdn_message(id),
    person_id      VARCHAR(64) NOT NULL,
    read_at        TIMESTAMPTZ,
    deliver_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',     -- PENDING / SENT / FAILED
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (message_id, person_id),
    CONSTRAINT ck_receiver_status CHECK (deliver_status IN ('PENDING','SENT','FAILED')));

-- 未读计数：person_id + read_at IS NULL 的部分索引
CREATE INDEX idx_receiver_unread ON fdn_message_receiver (person_id) WHERE read_at IS NULL;
-- 我的消息列表：按人员倒序翻页
CREATE INDEX idx_receiver_person ON fdn_message_receiver (person_id, create_at DESC);

-- 渠道模板
CREATE TABLE fdn_message_template (
    id         VARCHAR(32)  PRIMARY KEY,                      -- ULID，前缀 mtp_
    app_code   VARCHAR(64),
    code       VARCHAR(64)  NOT NULL,
    channel    VARCHAR(16)  NOT NULL,                          -- INBOX / MAIL / FEISHU
    title_tpl  VARCHAR(512) NOT NULL,
    body_tpl   TEXT         NOT NULL,
    data_label VARCHAR(16)  NOT NULL DEFAULT 'INTERNAL',
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',         -- ACTIVE / OFFLINE
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (app_code, code, channel),
    CONSTRAINT ck_template_channel CHECK (channel IN ('INBOX','MAIL','FEISHU')),
    CONSTRAINT ck_template_status CHECK (status IN ('ACTIVE','OFFLINE')));
