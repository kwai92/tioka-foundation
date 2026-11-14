package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 自管一次性导入批次实体（{@code fdn_self_managed_import}，F-05 BAS-27 §自管导入）。 */
@TableName("fdn_self_managed_import")
public class SelfManagedImportDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String subsystemId;

    private String status;

    private String summary;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubsystemId() {
        return subsystemId;
    }

    public void setSubsystemId(String subsystemId) {
        this.subsystemId = subsystemId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
