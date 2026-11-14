-- F-08 BAS-11 组织与账号同步基线（Foundation 库迁移 V28）：档案字段锁定位。
-- 本地锁定字段不被任何来源覆盖（S15 §10.2、ADR-078 第 5 条）；逗号分隔的字段名。

ALTER TABLE fdn_person   ADD COLUMN local_locked_fields VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE fdn_org_unit ADD COLUMN local_locked_fields VARCHAR(512) NOT NULL DEFAULT '';
