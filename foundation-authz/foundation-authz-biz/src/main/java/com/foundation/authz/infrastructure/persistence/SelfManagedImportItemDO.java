package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 自管一次性导入明细项实体（{@code fdn_self_managed_import_item}，F-05 BAS-27 §自管导入）。 */
@TableName("fdn_self_managed_import_item")
public class SelfManagedImportItemDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String batchId;

    private String kind;

    private String externalKey;

    private String payload;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
