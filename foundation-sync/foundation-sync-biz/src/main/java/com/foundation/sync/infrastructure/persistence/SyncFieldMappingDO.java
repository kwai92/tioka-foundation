package com.foundation.sync.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 字段映射实体（{@code fdn_sync_field_mapping}，F-08 BAS-11 §8）。无 {@code deleted} 列。 */
@TableName("fdn_sync_field_mapping")
public class SyncFieldMappingDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String sourceId;

    private String objectType;

    private String externalField;

    private String internalField;

    private String defaultValue;

    private String transform;

    private Boolean required;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getExternalField() {
        return externalField;
    }

    public void setExternalField(String externalField) {
        this.externalField = externalField;
    }

    public String getInternalField() {
        return internalField;
    }

    public void setInternalField(String internalField) {
        this.internalField = internalField;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getTransform() {
        return transform;
    }

    public void setTransform(String transform) {
        this.transform = transform;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }
}
