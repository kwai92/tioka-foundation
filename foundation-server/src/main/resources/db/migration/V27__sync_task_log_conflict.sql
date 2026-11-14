-- F-08 BAS-11 组织与账号同步基线（Foundation 库迁移 V27）：同步任务 / 日志 / 冲突。
-- 三者皆为运行期记录（只追加或状态流转），不参与逻辑删，故无 deleted 列（与审计/日志类表口径一致）。

CREATE TABLE fdn_sync_task (
    id                 VARCHAR(32) PRIMARY KEY,                     -- syn_{ulid}
    source_id          VARCHAR(32) NOT NULL REFERENCES fdn_sync_source (id),
    mode               VARCHAR(16) NOT NULL,                        -- FULL / INCREMENTAL / RECONCILE / PUSH
    trigger_mode       VARCHAR(16) NOT NULL,                        -- MANUAL / SCHEDULED / EVENT
    status             VARCHAR(16) NOT NULL DEFAULT 'RUNNING',      -- RUNNING / PAUSED / SUCCEEDED / FAILED / PARTIAL
    dry_run            BOOLEAN     NOT NULL DEFAULT FALSE,          -- 差异预演不落地
    cursor_json        TEXT,                                        -- 增量游标（时间戳 / 变更号）
    created_count      INT NOT NULL DEFAULT 0,
    updated_count      INT NOT NULL DEFAULT 0,
    disabled_count     INT NOT NULL DEFAULT 0,
    moved_count        INT NOT NULL DEFAULT 0,
    capability_missing BOOLEAN NOT NULL DEFAULT FALSE,              -- 报告含「能力缺失」标注
    pause_reason       VARCHAR(32),                                 -- THRESHOLD / null
    started_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at        TIMESTAMPTZ,
    create_by          VARCHAR(64) NOT NULL DEFAULT 'system',
    CONSTRAINT ck_sync_task_mode   CHECK (mode IN ('FULL','INCREMENTAL','RECONCILE','PUSH')),
    CONSTRAINT ck_sync_task_trigger CHECK (trigger_mode IN ('MANUAL','SCHEDULED','EVENT')),
    CONSTRAINT ck_sync_task_status CHECK (status IN ('RUNNING','PAUSED','SUCCEEDED','FAILED','PARTIAL')),
    CONSTRAINT ck_sync_task_pause  CHECK (pause_reason IS NULL OR pause_reason = 'THRESHOLD'));
CREATE INDEX idx_sync_task_source ON fdn_sync_task (source_id, started_at DESC);

CREATE TABLE fdn_sync_log (
    id          VARCHAR(32) PRIMARY KEY,                            -- slg_{ulid}
    task_id     VARCHAR(32) NOT NULL REFERENCES fdn_sync_task (id),
    level       VARCHAR(16) NOT NULL,                               -- INFO / WARN / ERROR
    object_type VARCHAR(32),
    external_id VARCHAR(255),
    action      VARCHAR(16),                                        -- CREATE / UPDATE / DISABLE / MOVE / SKIP / ERROR
    message     TEXT,
    create_at   TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_sync_log_task ON fdn_sync_log (task_id, create_at);

CREATE TABLE fdn_sync_conflict (
    id            VARCHAR(32) PRIMARY KEY,                          -- cfl_{ulid}
    task_id       VARCHAR(32) NOT NULL REFERENCES fdn_sync_task (id),
    source_id     VARCHAR(32) NOT NULL REFERENCES fdn_sync_source (id),
    object_type   VARCHAR(32) NOT NULL,
    external_id   VARCHAR(255) NOT NULL,
    conflict_type VARCHAR(32) NOT NULL,                             -- DUPLICATE_KEY / LOCKED_FIELD / AUTHORITY_CONFLICT / IDENTITY_AMBIGUOUS
    detail_json   TEXT,
    resolved      BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by   VARCHAR(64),
    create_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sync_conflict_type CHECK (conflict_type IN
        ('DUPLICATE_KEY','LOCKED_FIELD','AUTHORITY_CONFLICT','IDENTITY_AMBIGUOUS')));
CREATE INDEX idx_sync_conflict_task ON fdn_sync_conflict (task_id, create_at);
CREATE INDEX idx_sync_conflict_open ON fdn_sync_conflict (resolved, create_at);
