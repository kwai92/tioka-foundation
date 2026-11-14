-- F-09 BAS-19 三员分立与高风险授权复核（Foundation 库迁移 V24；ADR-029 第 60 条：无 tenant_id / workspace_id）
-- 三员角色（system_admin / security_admin / audit_admin）与互斥对（fdn_role_mutex）已在 BAS-05/V4 种子，
-- 本迁移只新增「高风险授权复核」表，并登记 BAS-19 引入的 fdn:* 权限码与其归属角色。
-- 说明：复核行在授权生效前保存「待生效授权的完整负载」（角色/对象/生效期），复核通过后才写入 fdn_role_grant，
--      因此高风险授权在通过前天然不生效、拒绝则永不生效（无需改动既有有效角色解析）。

CREATE TABLE fdn_high_risk_review (
    id                  VARCHAR(32)  PRIMARY KEY,               -- ULID，前缀 hrr_
    grant_id            VARCHAR(32)  NOT NULL,                  -- 待生效授权 ID（grt_…）
    permission_code     VARCHAR(128) NOT NULL,                  -- 触发复核的高风险权限码
    role_code           VARCHAR(64)  NOT NULL,
    subject_type        VARCHAR(16)  NOT NULL,                  -- PERSON / ORG_UNIT / POST
    subject_id          VARCHAR(64)  NOT NULL,
    include_descendants BOOLEAN      NOT NULL DEFAULT FALSE,
    effective_from      TIMESTAMPTZ,
    effective_to        TIMESTAMPTZ,
    source              VARCHAR(16)  NOT NULL DEFAULT 'MANUAL', -- MANUAL / RULE / SYNC
    requested_by        VARCHAR(64)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',-- PENDING / APPROVED / REJECTED
    reviewer_id         VARCHAR(64),
    reviewed_at         TIMESTAMPTZ,
    reason              VARCHAR(512),
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_hrisk_status CHECK (status IN ('PENDING','APPROVED','REJECTED')));
CREATE INDEX idx_hrisk_status ON fdn_high_risk_review (status, create_at);
CREATE INDEX idx_hrisk_grant  ON fdn_high_risk_review (grant_id);

-- 种子：BAS-19 引入的权限码（S15 §8.3 / F-09 §0.7）
INSERT INTO fdn_permission_code (id, app_id, code, name, risk_level) VALUES
  ('pms_fdn_audit_report',   'app_foundation', 'fdn:audit:report',    '上报审计',       'MEDIUM'),
  ('pms_fdn_audit_read',     'app_foundation', 'fdn:audit:read',      '按应用读审计',   'MEDIUM'),
  ('pms_fdn_audit_read_all', 'app_foundation', 'fdn:audit:read:all',  '读全部审计',     'HIGH'),
  ('pms_fdn_audit_export',   'app_foundation', 'fdn:audit:export',    '导出审计',       'HIGH'),
  ('pms_fdn_hrgrant_review', 'app_foundation', 'fdn:hrgrant:review',  '高风险授权复核', 'HIGH'),
  ('pms_fdn_separation_read','app_foundation', 'fdn:separation:read', '查看三员分立',   'LOW')
ON CONFLICT (app_id, code) DO NOTHING;

-- 种子：权限码 -> 内置角色（F-09 §0.7 归属表）
-- 安全管理员：高风险授权复核；审计管理员：读全部审计 + 导出（只读）；应用管理员：按应用读审计
INSERT INTO fdn_role_permission (role_code, code) VALUES
  ('security_admin','fdn:hrgrant:review'),
  ('audit_admin','fdn:audit:read'),('audit_admin','fdn:audit:read:all'),('audit_admin','fdn:audit:export'),
  ('app_admin','fdn:audit:read'),
  ('system_admin','fdn:separation:read'),('security_admin','fdn:separation:read'),
  ('audit_admin','fdn:separation:read')
ON CONFLICT (role_code, code) DO NOTHING;
