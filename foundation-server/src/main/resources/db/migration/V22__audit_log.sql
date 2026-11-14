-- F-09 BAS-10 审计与登录日志 —— 审计日志（Foundation 库迁移 V22；ADR-072 §1/§7/§8；ADR-029 第 60 条：无 tenant_id / workspace_id）
-- 硬约束：只增不改不删；主键 ULID；时间 TIMESTAMPTZ；按月 RANGE 分区（ADR-072 §7）。
-- 说明：分区表的主键必须包含分区键，故为 PRIMARY KEY (id, ts)（与 F-09 示意稿的 id 单列主键不同，PostgreSQL 要求）；
--      client_ip 用 VARCHAR(64) 而非 INET，避免 JDBC 类型映射对 MyBatis-Plus 的额外适配。

CREATE TABLE fdn_audit_log (
    id               VARCHAR(32)  NOT NULL,
    app_code         VARCHAR(64)  NOT NULL,
    action           VARCHAR(128) NOT NULL,
    resource_type    VARCHAR(64),
    resource_id      VARCHAR(64),
    outcome          VARCHAR(16)  NOT NULL,
    reason           VARCHAR(512),
    actor_type       VARCHAR(16)  NOT NULL,
    actor_id         VARCHAR(64)  NOT NULL,
    run_as           VARCHAR(64),
    root_principal   VARCHAR(64),
    on_behalf_of     VARCHAR(64),
    chain_json       TEXT,
    grant_id         VARCHAR(64),
    agent_version_id VARCHAR(32),
    client_ip        VARCHAR(64),
    user_agent       VARCHAR(512),
    origin           VARCHAR(16),
    trace_id         VARCHAR(64),
    request_id       VARCHAR(64)  NOT NULL,
    subject          VARCHAR(128),
    data_label       VARCHAR(16),
    before_hash      VARCHAR(64),
    after_hash       VARCHAR(64),
    details          JSONB,
    retention_class  VARCHAR(8)   NOT NULL,
    ts               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, ts),
    CONSTRAINT ck_audit_outcome   CHECK (outcome IN ('SUCCESS','FAILURE','DENIED','ERROR')),
    CONSTRAINT ck_audit_actor     CHECK (actor_type IN ('USER','SERVICE','AGENT','SYSTEM')),
    CONSTRAINT ck_audit_retention CHECK (retention_class IN ('SEC','CFG','BIZ','OPS'))
) PARTITION BY RANGE (ts);

-- 缺省分区：未预建月份的兜底，保证 UAT 可直接写入；按月分区的滚动与归档见 BAS-10 的归档任务
CREATE TABLE fdn_audit_log_default PARTITION OF fdn_audit_log DEFAULT;

CREATE INDEX idx_audit_app_ts  ON fdn_audit_log (app_code, ts DESC);
CREATE INDEX idx_audit_actor   ON fdn_audit_log (actor_id, ts DESC);
CREATE INDEX idx_audit_trace   ON fdn_audit_log (trace_id);
CREATE INDEX idx_audit_subject ON fdn_audit_log (subject) WHERE subject IS NOT NULL;

-- 审计链封印（GOV-09 / ADR-072 §8；哈希不写回审计行）
CREATE TABLE fdn_audit_seal (
    id           VARCHAR(32) PRIMARY KEY,
    window_from  TIMESTAMPTZ NOT NULL,
    window_to    TIMESTAMPTZ NOT NULL,
    prev_hash    VARCHAR(64),
    batch_hash   VARCHAR(64) NOT NULL,
    sealed_at    TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_audit_seal_window ON fdn_audit_seal (window_to DESC);

-- 上报批次幂等回执（POST /api/fdn/audit/events:batch 的 Idempotency-Key）
CREATE TABLE fdn_audit_report (
    id              VARCHAR(32)  PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    app_code        VARCHAR(64),
    accepted        INT          NOT NULL,
    create_at       TIMESTAMPTZ  NOT NULL DEFAULT now());
CREATE UNIQUE INDEX uk_audit_report_key ON fdn_audit_report (idempotency_key);
