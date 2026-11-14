-- F-09 BAS-18 §18.2 编号与序列（Foundation 库迁移 V32；ADR-029 第 60 条：无 tenant_id）
-- 编号规则与「某规则在某周期」的当前值；取号以数据库为单一权威、原子自增（并发唯一）。

CREATE TABLE fdn_sequence_rule (
    id          VARCHAR(32)  PRIMARY KEY,                     -- sqr_{ulid}
    code        VARCHAR(64)  NOT NULL,                        -- 业务码值，全局唯一
    name        VARCHAR(255) NOT NULL,
    pattern     VARCHAR(255) NOT NULL,                        -- 如 {prefix}{date:yyyyMMdd}{seq:4}{check}
    prefix      VARCHAR(32),
    seq_length  SMALLINT     NOT NULL DEFAULT 4,
    step        INT          NOT NULL DEFAULT 1,
    reset_cycle VARCHAR(8)   NOT NULL DEFAULT 'NONE',          -- NONE / DAILY / MONTHLY / YEARLY
    check_digit VARCHAR(8)   NOT NULL DEFAULT 'NONE',          -- NONE / MOD10 / MOD11
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE / INACTIVE
    sample      VARCHAR(64),                                   -- 预览样例
    create_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_sequence_reset_cycle CHECK (reset_cycle IN ('NONE','DAILY','MONTHLY','YEARLY')),
    CONSTRAINT ck_sequence_check_digit CHECK (check_digit IN ('NONE','MOD10','MOD11')),
    CONSTRAINT ck_sequence_status      CHECK (status IN ('ACTIVE','INACTIVE')),
    UNIQUE (code));

-- 主键 (rule_code, period_key)：周期切换自然落到新行、从 0 起算，无需定时重置任务。
CREATE TABLE fdn_sequence (
    rule_code  VARCHAR(64) NOT NULL,                           -- fdn_sequence_rule.code
    period_key VARCHAR(16) NOT NULL,                           -- '-' / yyyyMMdd / yyyyMM / yyyy
    current    BIGINT      NOT NULL DEFAULT 0,
    update_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (rule_code, period_key));

-- 种子：BAS-18 引入的权限码（S15 §14.1「公共服务」组）
INSERT INTO fdn_permission_code (id, app_id, code, name, risk_level) VALUES
  ('pms_fdn_sequence_admin', 'app_foundation', 'fdn:sequence:admin', '维护编号规则', 'MEDIUM')
ON CONFLICT (app_id, code) DO NOTHING;

INSERT INTO fdn_role_permission (role_code, code) VALUES
  ('system_admin','fdn:sequence:admin')
ON CONFLICT (role_code, code) DO NOTHING;

-- 种子：编号相关的枚举字典（§18.2「与字典的关系」，供管理台渲染；运行时不依赖）
INSERT INTO fdn_dict_type (id, code, name, app_code, status) VALUES
  ('dt_seq_reset_cycle', 'sequence_reset_cycle', '编号重置周期', NULL, 'ENABLED'),
  ('dt_seq_check_digit', 'sequence_check_digit', '编号校验位算法', NULL, 'ENABLED'),
  ('dt_seq_biz_code',    'sequence_biz_code',    '编号业务码值',   NULL, 'ENABLED')
ON CONFLICT DO NOTHING;

INSERT INTO fdn_dict_item (id, type_id, code, label, sort_no, status) VALUES
  ('di_src_none',    'dt_seq_reset_cycle', 'NONE',    '不重置',   1, 'ENABLED'),
  ('di_src_daily',   'dt_seq_reset_cycle', 'DAILY',   '按日重置', 2, 'ENABLED'),
  ('di_src_monthly', 'dt_seq_reset_cycle', 'MONTHLY', '按月重置', 3, 'ENABLED'),
  ('di_src_yearly',  'dt_seq_reset_cycle', 'YEARLY',  '按年重置', 4, 'ENABLED'),
  ('di_scd_none',    'dt_seq_check_digit', 'NONE',    '无校验位', 1, 'ENABLED'),
  ('di_scd_mod10',   'dt_seq_check_digit', 'MOD10',   'MOD10',    2, 'ENABLED'),
  ('di_scd_mod11',   'dt_seq_check_digit', 'MOD11',   'MOD11',    3, 'ENABLED'),
  ('di_sbc_contract','dt_seq_biz_code',    'contract','合同号',   1, 'ENABLED'),
  ('di_sbc_document','dt_seq_biz_code',    'document','单据号',   2, 'ENABLED'),
  ('di_sbc_workorder','dt_seq_biz_code',   'workorder','工单号',  3, 'ENABLED')
ON CONFLICT DO NOTHING;
