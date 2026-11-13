-- F-06 BAS-06 数据权限（Foundation 库迁移 V5；ADR-029 第 60 条：无 tenant_id）
-- 行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

-- 数据维度（可由清单声明或管理台新建；app_code 为空表示全局维度）
CREATE TABLE fdn_data_dimension (
    id           VARCHAR(32) PRIMARY KEY,
    app_code     VARCHAR(64),                              -- 空 = 全局维度
    code         VARCHAR(128) NOT NULL,                    -- 维度编码，创建后不可改
    name         VARCHAR(255) NOT NULL,
    value_source VARCHAR(16)  NOT NULL,                    -- DICT / ORG_TREE / APP_REPORTED
    hierarchical BOOLEAN NOT NULL DEFAULT FALSE,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE);
-- 全局与应用级各自唯一（NULL 在普通唯一约束中互不相等，故用部分唯一索引）
CREATE UNIQUE INDEX uq_data_dimension_global ON fdn_data_dimension (code) WHERE app_code IS NULL;
CREATE UNIQUE INDEX uq_data_dimension_app ON fdn_data_dimension (app_code, code) WHERE app_code IS NOT NULL;

-- 维度值（停用保留：active = false；重新上报复位为 true）
CREATE TABLE fdn_dimension_value (
    id           VARCHAR(32) PRIMARY KEY,
    dimension_id VARCHAR(32) NOT NULL REFERENCES fdn_data_dimension(id),
    code         VARCHAR(128) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    parent_id    VARCHAR(32),                              -- 层级维度的上级值 ID
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (dimension_id, code));
CREATE INDEX idx_dimension_value_dimension ON fdn_dimension_value (dimension_id);

-- 角色数据范围（一角色一行；部门五档 + CUSTOM 时的组织单元集合）
CREATE TABLE fdn_role_data_scope (
    id         VARCHAR(32) PRIMARY KEY,
    role_code  VARCHAR(64) NOT NULL UNIQUE REFERENCES fdn_role(code),
    dept_scope VARCHAR(16) NOT NULL DEFAULT 'SELF',        -- ALL / CUSTOM / DEPT / DEPT_AND_SUB / SELF
    dept_units TEXT,                                       -- CUSTOM 时的组织单元 ID 数组（JSON）
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE);

-- 角色维度取值（all_values = true 表示「全部」，此时 value_code 为空）
CREATE TABLE fdn_role_dimension_value (
    id           VARCHAR(32) PRIMARY KEY,
    role_code    VARCHAR(64) NOT NULL REFERENCES fdn_role(code),
    dimension_id VARCHAR(32) NOT NULL REFERENCES fdn_data_dimension(id),
    value_code   VARCHAR(128),
    all_values   BOOLEAN NOT NULL DEFAULT FALSE,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE);
CREATE INDEX idx_role_dimension_role ON fdn_role_dimension_value (role_code);

-- 人员维度授予（不走角色的额外授予；MANUAL 不被 RULE/SYNC 覆盖）
CREATE TABLE fdn_person_dimension (
    id           VARCHAR(32) PRIMARY KEY,
    person_id    VARCHAR(64) NOT NULL,
    dimension_id VARCHAR(32) NOT NULL REFERENCES fdn_data_dimension(id),
    value_code   VARCHAR(128),
    all_values   BOOLEAN NOT NULL DEFAULT FALSE,
    source       VARCHAR(16) NOT NULL DEFAULT 'MANUAL',    -- MANUAL / RULE / SYNC
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE);
CREATE INDEX idx_person_dimension_person ON fdn_person_dimension (person_id);
