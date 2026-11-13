-- F-05 BAS-04 应用登记与 OIDC 客户端（Foundation 库迁移 V3；ADR-029 第 60 条：无 tenant_id）

-- 应用（接入与登记面；目录与授权模式面 fdn_subsystem 见 BAS-27，二者以 app_id 1:1 关联）
CREATE TABLE fdn_app (
    id               VARCHAR(32)  PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL UNIQUE,          -- 编码不可改
    name             VARCHAR(255) NOT NULL,
    icon             VARCHAR(512),
    access_url       VARCHAR(512),
    kind             VARCHAR(16)  NOT NULL DEFAULT 'SUB',   -- PORTAL / ADMIN / SUB
    owner_user_id    VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    manifest_version BIGINT       NOT NULL DEFAULT 0,       -- 权限清单版本（BAS-05 导入时递增）
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system');

-- 客户端（密钥由 Keycloak 保管，本表不存密钥明文）
CREATE TABLE fdn_app_client (
    id                VARCHAR(32)  PRIMARY KEY,
    app_id            VARCHAR(32)  NOT NULL REFERENCES fdn_app(id),
    client_id         VARCHAR(128) NOT NULL,
    redirect_uris     TEXT         NOT NULL,
    grant_types       VARCHAR(255) NOT NULL DEFAULT 'authorization_code,refresh_token',
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / ROTATING / DISABLED
    secret_rotated_at TIMESTAMPTZ,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    UNIQUE (app_id, client_id));
CREATE INDEX idx_app_client_app ON fdn_app_client (app_id);

-- 服务账号（账号主体在 fdn_account，见 F-04/BAS-03；本表只存绑定与可用权限码白名单）
CREATE TABLE fdn_service_account (
    id               VARCHAR(32)  PRIMARY KEY,
    account_id       VARCHAR(32)  NOT NULL,                 -- fdn_account.id（type=SERVICE）
    app_id           VARCHAR(32)  NOT NULL REFERENCES fdn_app(id),
    client_id        VARCHAR(128) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    permission_codes TEXT,                                  -- JSON 数组
    owner_user_id    VARCHAR(64),
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    expires_at       TIMESTAMPTZ,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    UNIQUE (app_id, name));

-- 内置应用（F-05 BAS-04 §首次启动初始化）：门户与管理台登记为应用，口径与业务子系统一致。
-- 二者对 Keycloak 客户端（foundation-portal / foundation-admin）与回调地址由部署初始化落地（BAS-21）。
INSERT INTO fdn_app (id, code, name, kind, owner_user_id, status) VALUES
  ('app_portal', 'portal', 'Foundation 门户',   'PORTAL', 'system', 'ENABLED'),
  ('app_admin',  'admin',  'Foundation 管理台', 'ADMIN',  'system', 'ENABLED')
ON CONFLICT (code) DO NOTHING;
