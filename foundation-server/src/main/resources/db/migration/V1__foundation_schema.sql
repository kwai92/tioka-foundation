-- F-02 BAS-01：Foundation 库基线（自 V1 起，独立 Flyway 历史表，不占 Tioka 号段）
-- ADR-029 第 60 条：单企业独立部署，禁止 tenant_id / sys_tenant
CREATE EXTENSION IF NOT EXISTS pgcrypto;      -- 敏感字段加密（BAS-09 参数、后续账号）
CREATE EXTENSION IF NOT EXISTS pg_trgm;       -- 组织/人员模糊查询（后续工作项使用）

-- outbox 中继（foundation-starter-mq 与服务共用同一约定，事件体只带 ID、版本、变化类型，S15 §14.2）
CREATE TABLE fdn_outbox (
    id          VARCHAR(32)  PRIMARY KEY,             -- evt_{ulid}
    topic       VARCHAR(128) NOT NULL,
    event_type  VARCHAR(128) NOT NULL,
    aggregate   VARCHAR(64)  NOT NULL,
    payload     JSONB        NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'NEW',  -- NEW / SENT / DEAD
    retry_count INT          NOT NULL DEFAULT 0,
    create_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);
CREATE INDEX idx_fdn_outbox_unsent ON fdn_outbox (create_at) WHERE status = 'NEW';
