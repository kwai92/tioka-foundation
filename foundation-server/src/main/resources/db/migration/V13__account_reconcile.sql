-- F-04 BAS-03 每日对账记录（Foundation 库迁移 V13）。
-- 单实例执行（Redis 锁）；以 Foundation 为准修复人为改动；差异超阈值暂停修复并告警。只追加。

CREATE TABLE fdn_account_reconcile (
    id          VARCHAR(32) PRIMARY KEY,                            -- rec_{ulid}
    ran_at      TIMESTAMPTZ NOT NULL,
    total       INT         NOT NULL,                               -- 参与对账的账号总数
    drifted     INT         NOT NULL,                               -- 发现差异数
    fixed       INT         NOT NULL,                               -- 已修复数
    held        BOOLEAN     NOT NULL DEFAULT FALSE,                 -- 超阈值暂停修复待确认
    detail_json TEXT,                                               -- 差异明细
    create_at   TIMESTAMPTZ NOT NULL DEFAULT now());                -- 只追加
CREATE INDEX idx_account_reconcile_ran ON fdn_account_reconcile (ran_at);
