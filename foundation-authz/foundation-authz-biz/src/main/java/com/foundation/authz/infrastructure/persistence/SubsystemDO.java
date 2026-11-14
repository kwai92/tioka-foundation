package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 子系统实体（{@code fdn_subsystem}，F-05 BAS-27 §子系统）。 */
@TableName("fdn_subsystem")
public class SubsystemDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String appId;

    private String authMode;

    private Boolean portalVisible;

    private String adminUserId;

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

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public Boolean getPortalVisible() {
        return portalVisible;
    }

    public void setPortalVisible(Boolean portalVisible) {
        this.portalVisible = portalVisible;
    }

    public String getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(String adminUserId) {
        this.adminUserId = adminUserId;
    }
}
