-- F-03 BAS-02 组织主数据（Foundation 库迁移 V7；ADR-029 第 60 条：无 tenant_id）
-- 组织单元 / 岗位 / 职级。行级字段遵循 48-CS §4：create_at / update_at / create_by / update_by / deleted（逻辑删）。

CREATE TABLE fdn_org_unit (
    id               VARCHAR(32) PRIMARY KEY,                       -- org_{ulid}
    code             VARCHAR(64)  NOT NULL,                         -- 组织单元编码，全局唯一
    name             VARCHAR(255) NOT NULL,
    type             VARCHAR(16)  NOT NULL,                         -- GROUP / COMPANY / DEPARTMENT
    parent_id        VARCHAR(32)  REFERENCES fdn_org_unit (id),
    path             VARCHAR(512) NOT NULL,                         -- 从根到本节点的物化路径
    depth            SMALLINT     NOT NULL,                         -- 根为 1，≤ 10
    sort_no          INT          NOT NULL DEFAULT 0,
    leader_person_id VARCHAR(32),                                   -- 负责人（人员 ID）
    effective_at     TIMESTAMPTZ,                                   -- 生效时间
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE / DISABLED
    source           VARCHAR(32)  NOT NULL DEFAULT 'local',
    external_id      VARCHAR(255),
    version          BIGINT       NOT NULL DEFAULT 0,               -- 乐观锁 / 事件版本
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_org_unit_type  CHECK (type IN ('GROUP','COMPANY','DEPARTMENT')),
    CONSTRAINT ck_org_unit_depth CHECK (depth BETWEEN 1 AND 10),
    UNIQUE (code),
    UNIQUE (source, external_id));
CREATE INDEX idx_org_unit_parent ON fdn_org_unit (parent_id) WHERE deleted = FALSE;
-- 子树查询（BAS-06 数据范围）：按物化路径前缀取「本部门及以下」
CREATE INDEX idx_org_unit_path ON fdn_org_unit (path);

CREATE TABLE fdn_position (
    id          VARCHAR(32) PRIMARY KEY,                            -- pos_{ulid}
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    org_unit_id VARCHAR(32)  REFERENCES fdn_org_unit (id),          -- 可空 = 全局岗位
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    sort_no     INT          NOT NULL DEFAULT 0,
    source      VARCHAR(32)  NOT NULL DEFAULT 'local',
    external_id VARCHAR(255),
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_position_status CHECK (status IN ('ACTIVE','DISABLED')),
    UNIQUE (code),
    UNIQUE (source, external_id));
CREATE INDEX idx_position_org ON fdn_position (org_unit_id) WHERE deleted = FALSE;

CREATE TABLE fdn_job_grade (
    id          VARCHAR(32) PRIMARY KEY,                            -- grd_{ulid}
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    series      VARCHAR(64)  NOT NULL,                              -- 序列，如 M（管理）/ P（专业）
    grade_level SMALLINT     NOT NULL,                              -- 等级
    sort_no     INT          NOT NULL DEFAULT 0,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (code),
    UNIQUE (series, grade_level));
