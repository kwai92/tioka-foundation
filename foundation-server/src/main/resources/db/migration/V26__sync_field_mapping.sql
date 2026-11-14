-- F-08 BAS-11 组织与账号同步基线（Foundation 库迁移 V26）：字段映射（外部字段 → 内部字段，含默认值与转换）。

CREATE TABLE fdn_sync_field_mapping (
    id             VARCHAR(32)  PRIMARY KEY,                        -- map_{ulid}
    source_id      VARCHAR(32)  NOT NULL REFERENCES fdn_sync_source (id),
    object_type    VARCHAR(32)  NOT NULL,                           -- ORG_UNIT / PERSON / POSITION / EMPLOYMENT / JOB_GRADE
    external_field VARCHAR(128) NOT NULL,
    internal_field VARCHAR(128) NOT NULL,                           -- 人员 / 组织 / 岗位字段名
    default_value  VARCHAR(255),
    transform      VARCHAR(64),                                     -- FieldConverter 名称，可空
    required       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_at TIMESTAMPTZ NOT NULL DEFAULT now(), update_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, object_type, external_field));
CREATE INDEX idx_sync_field_mapping_source ON fdn_sync_field_mapping (source_id, object_type);
