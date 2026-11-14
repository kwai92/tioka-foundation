-- F-04 BAS-03 账号写操作待发记录（Foundation 库迁移 V12）。
-- 所有写操作先落 Foundation 库，再推送 Keycloak；推送失败进本表重试，对账任务兜底修复（F-04 §5）。
-- 明文口令与重置链接不落库、不进日志：payload_json 只含非敏感推送参数。

CREATE TABLE fdn_account_push (
    id            VARCHAR(32)  PRIMARY KEY,                         -- apu_{ulid}
    account_id    VARCHAR(32)  NOT NULL REFERENCES fdn_account (id),
    action        VARCHAR(32)  NOT NULL,                            -- CREATE_USER / SET_ENABLED / SET_REQUIRED_ACTIONS / RESET_PASSWORD / REVOKE_SESSIONS
    payload_json  TEXT,                                             -- 推送参数（不含口令与重置链接）
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',          -- PENDING / SENT / FAILED
    attempts      INT          NOT NULL DEFAULT 0,
    last_error    VARCHAR(512),
    next_retry_at TIMESTAMPTZ,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_account_push_status CHECK (status IN ('PENDING','SENT','FAILED')));
CREATE INDEX idx_account_push_pending ON fdn_account_push (status, next_retry_at) WHERE deleted = FALSE;
CREATE INDEX idx_account_push_account ON fdn_account_push (account_id);
