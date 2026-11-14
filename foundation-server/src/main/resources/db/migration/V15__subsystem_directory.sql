-- F-05 BAS-27 子系统与目录（Foundation 库迁移 V15；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

-- 子系统（目录与授权模式面；与 fdn_app 以 app_id 1:1 关联）
CREATE TABLE fdn_subsystem (
    id             VARCHAR(32) PRIMARY KEY,
    app_id         VARCHAR(32) NOT NULL UNIQUE REFERENCES fdn_app(id),
    auth_mode      VARCHAR(16) NOT NULL DEFAULT 'CONCENTRATED',  -- CONCENTRATED / GRADED / HYBRID / SELF
    portal_visible BOOLEAN NOT NULL DEFAULT TRUE,
    admin_user_id  VARCHAR(64),
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_subsystem_auth_mode CHECK (auth_mode IN ('CONCENTRATED','GRADED','HYBRID','SELF')));

-- 模块（子系统下第一层）
CREATE TABLE fdn_module (
    id           VARCHAR(32) PRIMARY KEY,
    subsystem_id VARCHAR(32) NOT NULL REFERENCES fdn_subsystem(id),
    code         VARCHAR(128) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    sort         INT NOT NULL DEFAULT 0,
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE / OFFLINE
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (subsystem_id, code),
    CONSTRAINT ck_module_status CHECK (status IN ('ACTIVE','OFFLINE')));

-- 功能（模块下第二层，可关联菜单）
CREATE TABLE fdn_function (
    id        VARCHAR(32) PRIMARY KEY,
    module_id VARCHAR(32) NOT NULL REFERENCES fdn_module(id),
    code      VARCHAR(128) NOT NULL,
    name      VARCHAR(255) NOT NULL,
    menu_id   VARCHAR(32),
    sort      INT NOT NULL DEFAULT 0,
    status    VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',           -- ACTIVE / OFFLINE
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (module_id, code),
    CONSTRAINT ck_function_status CHECK (status IN ('ACTIVE','OFFLINE')));
