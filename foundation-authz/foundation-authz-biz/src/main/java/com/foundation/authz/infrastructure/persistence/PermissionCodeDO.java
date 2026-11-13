package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

import java.time.Instant;

/** 权限码实体（{@code fdn_permission_code}）。 */
@TableName("fdn_permission_code")
public class PermissionCodeDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String appId;

    private String code;

    private String name;

    private String riskLevel;

    private String status;

    private Instant firstSeenAt;

    private Instant offlineAt;

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

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getOfflineAt() {
        return offlineAt;
    }

    public void setOfflineAt(Instant offlineAt) {
        this.offlineAt = offlineAt;
    }
}
