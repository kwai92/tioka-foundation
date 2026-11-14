-- F-09 BAS-10 登录日志（Foundation 库迁移 V23；来自 Keycloak 事件，event_id 幂等）
CREATE TABLE fdn_login_log (
    id               VARCHAR(32)  PRIMARY KEY,
    event_id         VARCHAR(128) NOT NULL,
    account_id       VARCHAR(32),
    login_name       VARCHAR(128),
    person_id        VARCHAR(64),
    keycloak_user_id VARCHAR(64),
    success          BOOLEAN      NOT NULL,
    auth_method      VARCHAR(32)  NOT NULL,
    client_id        VARCHAR(64),
    failure_reason   VARCHAR(255),
    client_ip        VARCHAR(64),
    user_agent       VARCHAR(512),
    ts               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (event_id));
CREATE INDEX idx_login_name_ts ON fdn_login_log (login_name, ts DESC);
CREATE INDEX idx_login_success ON fdn_login_log (success, ts DESC);
