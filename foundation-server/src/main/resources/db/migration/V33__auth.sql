-- F-04 BAS-15 登录方式：Keycloak 配置化与策略下发（Foundation 库迁移 V33；ADR-029 第 60 条：无 tenant_id）
-- 登录协议全部由 Keycloak 实现，Foundation 只做配置与管理：策略落库后经 Admin REST 下发到 realm。

CREATE TABLE fdn_auth_policy (
    id                   VARCHAR(32)  PRIMARY KEY,               -- pol_{ulid}（单行有效）
    password_policy      VARCHAR(512) NOT NULL,                 -- Keycloak passwordPolicy 字符串
    session_idle_minutes INT          NOT NULL DEFAULT 30,      -- SSO 会话空闲超时（分钟）
    session_max_hours    INT          NOT NULL DEFAULT 10,      -- SSO 会话最长有效（小时）
    concurrent_sessions  INT          NOT NULL DEFAULT 0,       -- 同账号并发会话上限（0 = 不限）
    totp_required_roles  VARCHAR(512) NOT NULL DEFAULT 'system_admin,security_admin',
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE TABLE fdn_auth_login_mode (
    id           VARCHAR(32) PRIMARY KEY,                       -- alm_{ulid}
    mode_code    VARCHAR(32) NOT NULL,                          -- password/oidc/ldap/saml/im-qr
    name         VARCHAR(64) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order   INT         NOT NULL DEFAULT 0,
    complex      BOOLEAN     NOT NULL DEFAULT FALSE,            -- 复杂项引导到 Keycloak 控制台
    console_hint VARCHAR(512),
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_login_mode CHECK (mode_code IN ('password','oidc','ldap','saml','im-qr')),
    UNIQUE (mode_code));

-- 种子：默认策略（S15 第 15 节默认值）
INSERT INTO fdn_auth_policy
    (id, password_policy, session_idle_minutes, session_max_hours, concurrent_sessions, totp_required_roles)
VALUES
    ('pol_default', 'length(8) and digits(1) and upperCase(1)', 30, 10, 0, 'system_admin,security_admin')
ON CONFLICT (id) DO NOTHING;

-- 种子：登录方式（F-04 §1 选型表；账号密码为本地凭证，其余为身份代理，复杂项引导到控制台）
INSERT INTO fdn_auth_login_mode (id, mode_code, name, enabled, sort_order, complex, console_hint) VALUES
  ('alm_password', 'password', '账号密码',              TRUE, 10, FALSE, NULL),
  ('alm_oidc',     'oidc',     '企业 IdP（OIDC）',      TRUE, 20, TRUE,
   '身份代理开通与元数据在 Keycloak 控制台 Identity Providers 完成'),
  ('alm_ldap',     'ldap',     'LDAP / AD',             TRUE, 30, TRUE,
   '用户联邦连接细节在 Keycloak 控制台 User Federation 完成'),
  ('alm_saml',     'saml',     'SAML 2.0',              TRUE, 40, TRUE,
   'SAML 元数据在 Keycloak 控制台 Identity Providers 完成'),
  ('alm_im_qr',    'im-qr',    'IM 扫码（飞书/企微/钉钉）', TRUE, 50, TRUE,
   'OAuth 提供方配置在 Keycloak 控制台 Identity Providers 完成')
ON CONFLICT (mode_code) DO NOTHING;

-- 种子：BAS-15 引入的权限码（F-04 §4）
INSERT INTO fdn_permission_code (id, app_id, code, name, risk_level) VALUES
  ('pms_fdn_auth_policy_read',  'app_foundation', 'fdn:auth:policy:read',  '读取登录策略', 'LOW'),
  ('pms_fdn_auth_policy_admin', 'app_foundation', 'fdn:auth:policy:admin', '修改登录策略', 'HIGH')
ON CONFLICT (app_id, code) DO NOTHING;

-- 种子：权限码 -> 内置角色（密码 / 会话策略属安全管理员职责；系统管理员只读）
INSERT INTO fdn_role_permission (role_code, code) VALUES
  ('security_admin','fdn:auth:policy:read'),('security_admin','fdn:auth:policy:admin'),
  ('system_admin','fdn:auth:policy:read')
ON CONFLICT (role_code, code) DO NOTHING;
