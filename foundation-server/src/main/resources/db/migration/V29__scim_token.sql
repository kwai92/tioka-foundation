-- F-08 BAS-13 SCIM 2.0 服务端（Foundation 库迁移 V29；ADR-029 第 60 条：无 tenant_id）
-- 下游 IdP（Keycloak / Entra ID / Okta）向 Foundation 推送组织与人员的令牌（BAS-13 §5）。
-- 令牌明文只在创建时返回一次；库内只存 SHA-256 哈希与识别用前缀。

CREATE TABLE fdn_scim_token (
    id           VARCHAR(32)  PRIMARY KEY,                          -- sct_{ulid}
    name         VARCHAR(128) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,                             -- SHA-256；明文只显示一次
    prefix       VARCHAR(16)  NOT NULL,                             -- 识别用前缀
    scopes       VARCHAR(255) NOT NULL DEFAULT '',                  -- Users / Groups 读写子集
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',           -- ENABLED / DISABLED
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    create_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_scim_token_status CHECK (status IN ('ENABLED','DISABLED')),
    UNIQUE (prefix));
-- 管理面接口沿用 BAS-11 的权限码（fdn:sync:admin），不新增权限码种子。
