-- F-09 BAS-18 §18.1 工作日历（Foundation 库迁移 V31；ADR-029 第 60 条：无 tenant_id）
-- 区域 + 年度的日历头与逐日类型；供期限 / 逾期 / SLA 的「N 个工作日后」计算（S15 §13）。

CREATE TABLE fdn_calendar (
    id          VARCHAR(32)  PRIMARY KEY,                     -- cal_{ulid}
    region_code VARCHAR(16)  NOT NULL DEFAULT 'CN',           -- 区域（默认为中国）
    year        SMALLINT     NOT NULL,
    name        VARCHAR(128) NOT NULL,
    source      VARCHAR(16)  NOT NULL DEFAULT 'CUSTOM',        -- OFFICIAL / CUSTOM
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE / INACTIVE
    create_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_calendar_source CHECK (source IN ('OFFICIAL','CUSTOM')),
    CONSTRAINT ck_calendar_status CHECK (status IN ('ACTIVE','INACTIVE')),
    UNIQUE (region_code, year));

CREATE TABLE fdn_calendar_day (
    id          VARCHAR(32) PRIMARY KEY,                      -- cdd_{ulid}
    calendar_id VARCHAR(32) NOT NULL REFERENCES fdn_calendar(id),
    day_date    DATE        NOT NULL,
    day_type    VARCHAR(24) NOT NULL,                         -- WORKDAY/RESTDAY/HOLIDAY/ADJUSTED_WORKDAY
    is_workday  BOOLEAN     NOT NULL,                         -- 冗余存储，便于快速判定
    name        VARCHAR(128),
    CONSTRAINT ck_calendar_day_type CHECK (day_type IN
        ('WORKDAY','RESTDAY','HOLIDAY','ADJUSTED_WORKDAY')),
    UNIQUE (calendar_id, day_date));
CREATE INDEX idx_calendar_day_date ON fdn_calendar_day (day_date);

-- 种子：BAS-18 引入的权限码（S15 §14.1「公共服务」组）
INSERT INTO fdn_permission_code (id, app_id, code, name, risk_level) VALUES
  ('pms_fdn_calendar_admin', 'app_foundation', 'fdn:calendar:admin', '维护工作日历（节假日与调休）', 'MEDIUM')
ON CONFLICT (app_id, code) DO NOTHING;

-- 种子：权限码 -> 内置角色（系统管理员维护日历）
INSERT INTO fdn_role_permission (role_code, code) VALUES
  ('system_admin','fdn:calendar:admin')
ON CONFLICT (role_code, code) DO NOTHING;
