package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 权限清单导入记录（{@code fdn_app_manifest}）。
 *
 * <p>只追加表：无审计列与逻辑删列，故不继承 {@code BaseDO}（48-CS §4 的
 * {@code AppendOnlyDO} 随 BAS-24 补齐后改继承之）。
 */
@TableName("fdn_app_manifest")
public class AppManifestDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String appId;

    private Long version;

    private String contentHash;

    private Instant importedAt;

    private String importedBy;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public String getImportedBy() {
        return importedBy;
    }

    public void setImportedBy(String importedBy) {
        this.importedBy = importedBy;
    }
}
