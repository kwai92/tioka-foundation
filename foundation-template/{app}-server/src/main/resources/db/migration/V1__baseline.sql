-- {app}-server/src/main/resources/db/migration/V1__baseline.sql
-- 基线迁移（BAS-25 工程模板）：编号自 V1 起，下游子系统在本模板基础上顺延自己的号段。
--
-- 硬约束（ADR-029 第 60 条：禁止 tenant_id）：单企业独立部署，不引入租户维度列。
-- 标识：主键形态 {前缀}_{ulid}，列类型 TEXT；时间：TIMESTAMPTZ（DO 字段 LocalDateTime，UTC 语义）。

-- 默认只启用所需扩展（S15 §11.4 名称/模糊检索）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 示例表（默认注释掉，供子系统按需启用；示范主键与时间列写法，禁止加租户维度列）：
-- CREATE TABLE example (
--     id        TEXT        PRIMARY KEY,          -- {前缀}_{ulid}，如 ex_01H...
--     name      TEXT        NOT NULL,
--     create_at TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 创建时间（UTC）
--     update_at TIMESTAMPTZ NOT NULL DEFAULT now()   -- 更新时间（UTC）
-- );
--
-- CREATE INDEX idx_example_name_trgm ON example USING gin (name gin_trgm_ops);
