-- F-04 BAS-03 外部身份（Foundation 库迁移 V11）：统一记录「外部身份 → 人员」的关联。
-- person_id 为 NULL 即一条待处理关联；不允许仅凭姓名匹配、不自动建人（ADR-026、ADR-052 第 3 条）。

CREATE TABLE fdn_external_identity (
    id          VARCHAR(32)  PRIMARY KEY,                           -- xid_{ulid}
    source      VARCHAR(32)  NOT NULL,                              -- FEISHU_OPEN_ID / FEISHU_UNION_ID / DINGTALK_USER_ID / WECOM_USER_ID / AD_OBJECTGUID / SCIM_EXTERNAL_ID
    external_id VARCHAR(255) NOT NULL,
    person_id   VARCHAR(32)  REFERENCES fdn_person (id),            -- 可空 = 待处理关联
    link_mode   VARCHAR(16),                                        -- AUTO / SELF_CONFIRMED / ADMIN
    linked_at   TIMESTAMPTZ,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    create_by VARCHAR(64) NOT NULL DEFAULT 'system', update_by VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_external_identity_source CHECK (source IN
        ('FEISHU_OPEN_ID','FEISHU_UNION_ID','DINGTALK_USER_ID','WECOM_USER_ID','AD_OBJECTGUID','SCIM_EXTERNAL_ID')),
    CONSTRAINT ck_external_identity_link_mode CHECK (link_mode IS NULL OR link_mode IN ('AUTO','SELF_CONFIRMED','ADMIN')));
CREATE UNIQUE INDEX uk_external_identity_source_external ON fdn_external_identity (source, external_id)
    WHERE deleted = FALSE;
CREATE INDEX idx_external_identity_person ON fdn_external_identity (person_id);
