-- F-03 BAS-02 组织主数据（Foundation 库迁移 V8）：人员 / 任职 / 汇报关系
-- 手机与邮箱加密存储（*_cipher，AES-256-GCM）并以 *_hash 命中查重（F-03 §2.1）。

CREATE TABLE fdn_person (
    id            VARCHAR(32) PRIMARY KEY,                          -- per_{ulid}
    emp_no        VARCHAR(64)  NOT NULL,                            -- 工号，唯一（数字员工亦赋工号）
    name          VARCHAR(255) NOT NULL,
    type          VARCHAR(16)  NOT NULL,                            -- EMPLOYEE / OUTSOURCED / DIGITAL_EMPLOYEE
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',           -- ACTIVE / DISABLED / LEFT
    mobile_cipher TEXT, mobile_hash VARCHAR(64),                    -- 手机：密文 + 查重哈希
    email_cipher  TEXT, email_hash  VARCHAR(64),                    -- 邮箱：密文 + 查重哈希
    source        VARCHAR(32)  NOT NULL DEFAULT 'local',
    external_id   VARCHAR(255),
    version       BIGINT       NOT NULL DEFAULT 0,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_person_type CHECK (type IN ('EMPLOYEE','OUTSOURCED','DIGITAL_EMPLOYEE')),
    CONSTRAINT ck_person_status CHECK (status IN ('ACTIVE','DISABLED','LEFT')),
    UNIQUE (emp_no),
    UNIQUE (source, external_id));
CREATE INDEX idx_person_mobile_hash ON fdn_person (mobile_hash);
CREATE INDEX idx_person_email_hash  ON fdn_person (email_hash);

CREATE TABLE fdn_employment (
    id          VARCHAR(32) PRIMARY KEY,                            -- emp_{ulid}
    person_id   VARCHAR(32) NOT NULL REFERENCES fdn_person (id),
    org_unit_id VARCHAR(32) NOT NULL REFERENCES fdn_org_unit (id),
    position_id VARCHAR(32) REFERENCES fdn_position (id),           -- 可空
    is_primary  BOOLEAN     NOT NULL DEFAULT FALSE,
    start_date  DATE        NOT NULL,
    end_date    DATE,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_employment_range CHECK (end_date IS NULL OR end_date >= start_date));
-- 一人恰一个主任职：在未结束（end_date IS NULL）的主任职上唯一
CREATE UNIQUE INDEX uq_employment_primary ON fdn_employment (person_id)
    WHERE is_primary AND end_date IS NULL AND deleted = FALSE;
CREATE INDEX idx_employment_person ON fdn_employment (person_id);
CREATE INDEX idx_employment_org    ON fdn_employment (org_unit_id) WHERE end_date IS NULL AND deleted = FALSE;

CREATE TABLE fdn_report_line (
    person_id         VARCHAR(32) NOT NULL REFERENCES fdn_person (id),
    manager_person_id VARCHAR(32) NOT NULL REFERENCES fdn_person (id),
    source            VARCHAR(16) NOT NULL DEFAULT 'local',         -- local / derived
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (person_id),
    CONSTRAINT ck_report_not_self CHECK (person_id <> manager_person_id));
