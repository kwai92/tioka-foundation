-- F-08 BAS-11 组织与账号同步基线（Foundation 库迁移 V25；ADR-029 第 60 条：无 tenant_id）
-- 同步来源登记与能力声明（ADR-078 第 1、2 条）：一个部署只有一个权威来源。

CREATE TABLE fdn_sync_source (
    id                VARCHAR(32)  PRIMARY KEY,                     -- src_{ulid}
    code              VARCHAR(64)  NOT NULL,                        -- 来源编码，全局唯一
    name              VARCHAR(128) NOT NULL,
    source_type       VARCHAR(32)  NOT NULL,                        -- FEISHU / LDAP / SCIM / LOCAL / WECOM ...
    authority         BOOLEAN      NOT NULL DEFAULT FALSE,          -- 是否权威来源
    credential_ref    VARCHAR(255),                                 -- 凭证引用（ADR-042 密钥来源，只存引用）
    adapter_impl      VARCHAR(255) NOT NULL,                        -- 插件适配器类名
    plugin_sha256     VARCHAR(64),                                  -- 插件包哈希（FND-12 精简宿主校验）
    capabilities_json TEXT         NOT NULL DEFAULT '{}',           -- 能力声明缓存（SourceCapabilities）
    config_json       TEXT         NOT NULL DEFAULT '{}',           -- 来源私有配置（不含密钥）
    status            VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',      -- ENABLED / DISABLED
    schedule_cron     VARCHAR(64),                                  -- 定时增量 / 全量对账计划
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    CONSTRAINT ck_sync_source_status CHECK (status IN ('ENABLED','DISABLED')),
    UNIQUE (code));
-- 一个部署只有一个权威来源（ADR-052 第 4 条）
CREATE UNIQUE INDEX uq_sync_source_authority ON fdn_sync_source (authority) WHERE authority = TRUE;

-- 种子：BAS-11 引入的权限码（S15 §14.1「同步」分组）
INSERT INTO fdn_permission_code (id, app_id, code, name, risk_level) VALUES
  ('pms_fdn_sync_read',  'app_foundation', 'fdn:sync:read',  '查看同步来源与任务', 'LOW'),
  ('pms_fdn_sync_admin', 'app_foundation', 'fdn:sync:admin', '同步来源与落地管理', 'HIGH')
ON CONFLICT (app_id, code) DO NOTHING;

-- 种子：权限码 -> 内置角色（平台管理员负责来源登记与触发）
INSERT INTO fdn_role_permission (role_code, code) VALUES
  ('system_admin','fdn:sync:read'),('system_admin','fdn:sync:admin')
ON CONFLICT (role_code, code) DO NOTHING;
