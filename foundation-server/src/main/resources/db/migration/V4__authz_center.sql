-- F-05 BAS-05 权限中心（Foundation 库迁移 V4；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

-- Foundation 自身应用：承载 fdn:* 平台权限码（全局角色只含这类权限码）
INSERT INTO fdn_app (id, code, name, kind, owner_user_id, status) VALUES
  ('app_foundation', 'foundation', 'Foundation 平台', 'ADMIN', 'system', 'ENABLED')
ON CONFLICT (code) DO NOTHING;

-- 权限清单（只追加：同哈希不重复导入；版本只能前进）
CREATE TABLE fdn_app_manifest (
    id            VARCHAR(32) PRIMARY KEY,
    app_id        VARCHAR(32) NOT NULL REFERENCES fdn_app(id),
    version       BIGINT      NOT NULL,
    content_hash  VARCHAR(64) NOT NULL,                    -- SHA-256（RFC 8785 规范化后）
    imported_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    imported_by   VARCHAR(64) NOT NULL,
    UNIQUE (app_id, content_hash),
    UNIQUE (app_id, version));

-- 菜单（来自清单；管理台只能改名称/图标/排序/显示）
CREATE TABLE fdn_menu (
    id              VARCHAR(32) PRIMARY KEY,
    app_id          VARCHAR(32) NOT NULL REFERENCES fdn_app(id),
    code            VARCHAR(128) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(16)  NOT NULL,                 -- DIR / MENU / BUTTON
    parent_id       VARCHAR(32),
    route_path      VARCHAR(512),
    component       VARCHAR(255),
    icon            VARCHAR(512),
    sort            INT NOT NULL DEFAULT 0,
    visible         BOOLEAN NOT NULL DEFAULT TRUE,
    permission_code VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / OFFLINE
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (app_id, code));
CREATE INDEX idx_menu_app ON fdn_menu (app_id);

-- 权限码（资源:动作；消失后标 OFFLINE，保留 90 天后清理）
CREATE TABLE fdn_permission_code (
    id            VARCHAR(32) PRIMARY KEY,
    app_id        VARCHAR(32) NOT NULL REFERENCES fdn_app(id),
    code          VARCHAR(128) NOT NULL,
    name          VARCHAR(255),
    risk_level    VARCHAR(16) NOT NULL DEFAULT 'LOW',      -- LOW / MEDIUM / HIGH
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / OFFLINE
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    offline_at    TIMESTAMPTZ,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (app_id, code));
CREATE INDEX idx_permission_app ON fdn_permission_code (app_id);

-- 角色与角色权限
CREATE TABLE fdn_role (
    code        VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    scope       VARCHAR(16) NOT NULL,                      -- SYSTEM / APP
    app_id      VARCHAR(32),                               -- scope=APP 时非空
    built_in    BOOLEAN NOT NULL DEFAULT FALSE,
    data_scope  VARCHAR(32) NOT NULL DEFAULT 'SELF',       -- ALL/DEPTS/DEPT/DEPT_AND_CHILD/SELF/CUSTOM（BAS-06 细化）
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE);
CREATE TABLE fdn_role_permission (
    role_code VARCHAR(64) NOT NULL REFERENCES fdn_role(code),
    code      VARCHAR(128) NOT NULL,
    PRIMARY KEY (role_code, code));
CREATE TABLE fdn_role_mutex (                              -- 互斥角色（三员互斥）
    role_code       VARCHAR(64) NOT NULL,
    mutex_role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, mutex_role_code));

-- 授权（人员/组织单元/岗位；同步与规则产生的授权不覆盖手工授权）
CREATE TABLE fdn_role_grant (
    id                   VARCHAR(32) PRIMARY KEY,
    role_code            VARCHAR(64) NOT NULL REFERENCES fdn_role(code),
    subject_type         VARCHAR(16) NOT NULL,              -- PERSON / ORG_UNIT / POST
    subject_id           VARCHAR(64) NOT NULL,
    include_descendants  BOOLEAN NOT NULL DEFAULT FALSE,    -- 仅 ORG_UNIT 有意义
    effective_from       TIMESTAMPTZ,
    effective_to         TIMESTAMPTZ,
    source               VARCHAR(16) NOT NULL DEFAULT 'MANUAL', -- MANUAL / RULE / SYNC
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE);
CREATE INDEX idx_grant_subject ON fdn_role_grant (subject_type, subject_id);
CREATE INDEX idx_grant_role ON fdn_role_grant (role_code);

-- 种子：Foundation 自身权限码（fdn:*）
INSERT INTO fdn_permission_code (id, app_id, code, name, risk_level) VALUES
  ('pms_fdn_manifest_import', 'app_foundation', 'fdn:manifest:import', '导入权限清单', 'MEDIUM'),
  ('pms_fdn_menu_read',       'app_foundation', 'fdn:menu:read',       '查看菜单',     'LOW'),
  ('pms_fdn_menu_admin',      'app_foundation', 'fdn:menu:admin',      '调整菜单显示', 'MEDIUM'),
  ('pms_fdn_role_read',       'app_foundation', 'fdn:role:read',       '查看角色',     'LOW'),
  ('pms_fdn_role_admin',      'app_foundation', 'fdn:role:admin',      '维护角色',     'HIGH'),
  ('pms_fdn_grant_admin',     'app_foundation', 'fdn:grant:admin',     '维护授权',     'HIGH'),
  ('pms_fdn_permission_read', 'app_foundation', 'fdn:permission:read', '查看权限码目录', 'LOW')
ON CONFLICT (app_id, code) DO NOTHING;

-- 种子：内置全局角色（三员 + 组织管理员 + 应用管理员）
INSERT INTO fdn_role (code, name, scope, built_in, data_scope) VALUES
  ('system_admin',   '系统管理员', 'SYSTEM', TRUE, 'ALL'),
  ('security_admin', '安全管理员', 'SYSTEM', TRUE, 'ALL'),
  ('audit_admin',    '审计管理员', 'SYSTEM', TRUE, 'ALL'),
  ('org_admin',      '组织管理员', 'SYSTEM', TRUE, 'ALL'),
  ('app_admin',      '应用管理员', 'SYSTEM', TRUE, 'ALL')
ON CONFLICT (code) DO NOTHING;

-- 种子：内置角色 -> fdn:* 权限码（基线，BAS-27 分级管理员细化）
INSERT INTO fdn_role_permission (role_code, code) VALUES
  ('system_admin','fdn:manifest:import'),('system_admin','fdn:menu:read'),('system_admin','fdn:menu:admin'),
  ('system_admin','fdn:role:read'),('system_admin','fdn:role:admin'),('system_admin','fdn:grant:admin'),
  ('system_admin','fdn:permission:read'),
  ('security_admin','fdn:role:read'),('security_admin','fdn:role:admin'),('security_admin','fdn:grant:admin'),
  ('security_admin','fdn:permission:read'),
  ('audit_admin','fdn:role:read'),('audit_admin','fdn:permission:read'),('audit_admin','fdn:menu:read'),
  ('org_admin','fdn:grant:admin'),('org_admin','fdn:role:read'),
  ('app_admin','fdn:manifest:import'),('app_admin','fdn:menu:read'),('app_admin','fdn:menu:admin'),
  ('app_admin','fdn:role:read'),('app_admin','fdn:permission:read')
ON CONFLICT (role_code, code) DO NOTHING;

-- 种子：三员互斥
INSERT INTO fdn_role_mutex (role_code, mutex_role_code) VALUES
  ('system_admin','security_admin'),('system_admin','audit_admin'),('security_admin','audit_admin')
ON CONFLICT (role_code, mutex_role_code) DO NOTHING;
