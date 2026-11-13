-- F-03 BAS-02 组织主数据（Foundation 库迁移 V9）：变更历史（只追加）
-- 与 BAS-10 审计管道并存不替代（F-03 §4.3）；本表不做逻辑删、无 update_* 列。

CREATE TABLE fdn_org_change_log (
    id          VARCHAR(32) PRIMARY KEY,                            -- chg_{ulid}
    entity_type VARCHAR(32) NOT NULL,                              -- ORG_UNIT / POSITION / JOB_GRADE / PERSON / EMPLOYMENT / REPORT_LINE
    entity_id   VARCHAR(32) NOT NULL,
    action      VARCHAR(16) NOT NULL,                              -- CREATE / UPDATE / MOVE / DISABLE / IMPORT
    before_json TEXT, after_json TEXT,                             -- 字段白名单摘要
    source      VARCHAR(16) NOT NULL DEFAULT 'manual',             -- manual / import / sync / event
    actor       VARCHAR(64) NOT NULL DEFAULT 'system',
    create_at   TIMESTAMPTZ NOT NULL DEFAULT now());               -- 只追加
CREATE INDEX idx_org_change_entity ON fdn_org_change_log (entity_type, entity_id, create_at);
