package com.foundation.app.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.MutableRecordDO;

/** 应用实体（{@code fdn_app}，F-05 BAS-04 §迁移 V3；无 {@code deleted} 列）。 */
@TableName("fdn_app")
public class AppDO extends MutableRecordDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    private String icon;

    private String accessUrl;

    private String kind;

    private String ownerUserId;

    private String status;

    private Long manifestVersion;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getAccessUrl() {
        return accessUrl;
    }

    public void setAccessUrl(String accessUrl) {
        this.accessUrl = accessUrl;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(Long manifestVersion) {
        this.manifestVersion = manifestVersion;
    }
}
