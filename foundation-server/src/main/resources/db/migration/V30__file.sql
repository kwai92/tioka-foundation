-- F-09 BAS-17 文件服务（Foundation 库迁移 V30；ADR-029 第 60 条：无 tenant_id）
-- 文件对象与「业务对象引用文件」关系；存储抽象见 ADR-026，数据标签见 ADR-040，删除语义见 ADR-029 第 13 条。

CREATE TABLE fdn_file (
    id           VARCHAR(32)  PRIMARY KEY,                    -- fil_{ulid}
    app_code     VARCHAR(64)  NOT NULL,                       -- 归属应用（隔离边界）
    biz_type     VARCHAR(64),                                 -- 主业务对象类型（可选）
    biz_id       VARCHAR(64),                                 -- 主业务对象 ID（可选）
    file_name    VARCHAR(512) NOT NULL,
    content_type VARCHAR(128),
    size_bytes   BIGINT       NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    storage_kind VARCHAR(16)  NOT NULL,                       -- LOCAL / S3
    storage_key  VARCHAR(512) NOT NULL,                       -- 对象键（不含桶；桶由配置）
    data_label   VARCHAR(16)  NOT NULL DEFAULT 'INTERNAL',    -- ADR-040
    scan_status  VARCHAR(16)  NOT NULL DEFAULT 'SKIPPED',     -- PENDING / CLEAN / INFECTED / SKIPPED
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE / DELETED
    uploaded_by  VARCHAR(64)  NOT NULL,
    create_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_file_storage_kind CHECK (storage_kind IN ('LOCAL','S3')),
    CONSTRAINT ck_file_scan_status  CHECK (scan_status  IN ('PENDING','CLEAN','INFECTED','SKIPPED')),
    CONSTRAINT ck_file_status       CHECK (status       IN ('ACTIVE','DELETED'))
);
-- 按应用的时间倒序列表（文件中心 / 归档查询）
CREATE INDEX idx_file_app_ts ON fdn_file (app_code, create_at DESC);
-- 同应用内按 sha256 校验与复用
CREATE INDEX idx_file_sha    ON fdn_file (app_code, sha256);

CREATE TABLE fdn_file_ref (
    id         VARCHAR(32) PRIMARY KEY,                       -- frf_{ulid}
    file_id    VARCHAR(32) NOT NULL REFERENCES fdn_file(id),
    app_code   VARCHAR(64) NOT NULL,
    biz_type   VARCHAR(64) NOT NULL,
    biz_id     VARCHAR(64) NOT NULL,
    create_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (file_id, app_code, biz_type, biz_id));
CREATE INDEX idx_file_ref_biz ON fdn_file_ref (app_code, biz_type, biz_id);

-- 种子：BAS-17 引入的权限码（S15 §14.1「公共服务」组）
INSERT INTO fdn_permission_code (id, app_id, code, name, risk_level) VALUES
  ('pms_fdn_file_upload', 'app_foundation', 'fdn:file:upload', '上传与引用文件', 'MEDIUM'),
  ('pms_fdn_file_read',   'app_foundation', 'fdn:file:read',   '读取与下载文件', 'LOW')
ON CONFLICT (app_id, code) DO NOTHING;

-- 种子：权限码 -> 内置角色（应用管理员管自己应用的文件；平台管理员为超集）
INSERT INTO fdn_role_permission (role_code, code) VALUES
  ('app_admin','fdn:file:upload'),('app_admin','fdn:file:read'),
  ('system_admin','fdn:file:upload'),('system_admin','fdn:file:read')
ON CONFLICT (role_code, code) DO NOTHING;
