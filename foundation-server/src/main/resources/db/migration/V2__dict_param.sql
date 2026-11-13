-- F-02 BAS-09：字典与参数（S15 §13）
-- 硬约束：不带 tenant_id（ADR-029 第 60 条）；主键 ULID（{前缀}_{ulid}）；时间列 TIMESTAMPTZ

CREATE TABLE fdn_dict_type (
    id         VARCHAR(32)  PRIMARY KEY,             -- dt_{ulid}
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    app_code   VARCHAR(64),                          -- NULL=全局；否则归属应用
    status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    create_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    create_by  VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by  VARCHAR(64)  NOT NULL DEFAULT 'system'
);
-- 全局项与归属项分别唯一（PostgreSQL 中 NULL 互不相等，故拆两个部分唯一索引）
CREATE UNIQUE INDEX uq_dict_type_global ON fdn_dict_type (code) WHERE app_code IS NULL;
CREATE UNIQUE INDEX uq_dict_type_app    ON fdn_dict_type (app_code, code) WHERE app_code IS NOT NULL;

CREATE TABLE fdn_dict_item (
    id         VARCHAR(32)  PRIMARY KEY,             -- di_{ulid}
    type_id    VARCHAR(32)  NOT NULL REFERENCES fdn_dict_type(id),
    code       VARCHAR(64)  NOT NULL,
    label      VARCHAR(128) NOT NULL,
    sort_no    INT          NOT NULL DEFAULT 0,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    style_tag  VARCHAR(32),                          -- 样式标签（S15 §13）
    ext_json   JSONB,
    create_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (type_id, code)
);
CREATE INDEX idx_dict_item_type ON fdn_dict_item (type_id, sort_no);

CREATE TABLE fdn_param (
    id           VARCHAR(32)  PRIMARY KEY,           -- pm_{ulid}
    app_code     VARCHAR(64),                        -- NULL=全局
    param_key    VARCHAR(128) NOT NULL,
    param_value  TEXT,                               -- secret=true 时存 AES-256-GCM 密文
    secret       BOOLEAN      NOT NULL DEFAULT FALSE,
    description  VARCHAR(255),
    update_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by    VARCHAR(64)  NOT NULL DEFAULT 'system'
);
CREATE UNIQUE INDEX uq_param_global ON fdn_param (param_key) WHERE app_code IS NULL;
CREATE UNIQUE INDEX uq_param_app    ON fdn_param (app_code, param_key) WHERE app_code IS NOT NULL;

-- 每次修改记旧值与新值摘要（S15 §13）；只存摘要，不存旧明文
CREATE TABLE fdn_param_history (
    id          VARCHAR(32)  PRIMARY KEY,            -- ph_{ulid}
    param_id    VARCHAR(32)  NOT NULL REFERENCES fdn_param(id),
    old_digest  VARCHAR(128),                        -- 旧值 SHA-256（首次为空）
    new_digest  VARCHAR(128) NOT NULL,
    changed_by  VARCHAR(64)  NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_param_history ON fdn_param_history (param_id, changed_at DESC);
