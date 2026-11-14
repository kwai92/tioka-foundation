package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 组织分级管理员范围实体（{@code fdn_org_admin_scope}，F-05 BAS-27 §分级授权）。 */
@TableName("fdn_org_admin_scope")
public class OrgAdminScopeDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String userId;

    private String orgUnitId;

    private Boolean includeDescendants;

    /** 可授予角色白名单（JSON 数组文本；空表示不限）。 */
    private String grantableRoles;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(String orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public Boolean getIncludeDescendants() {
        return includeDescendants;
    }

    public void setIncludeDescendants(Boolean includeDescendants) {
        this.includeDescendants = includeDescendants;
    }

    public String getGrantableRoles() {
        return grantableRoles;
    }

    public void setGrantableRoles(String grantableRoles) {
        this.grantableRoles = grantableRoles;
    }
}
