package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 角色-权限码关联（{@code fdn_role_permission}）。
 *
 * <p>关联表：复合主键、无审计列，故不继承 {@code BaseDO}（可整体替换，按角色物理重写）。
 */
@TableName("fdn_role_permission")
public class RolePermissionDO {

    private String roleCode;

    private String code;

    public RolePermissionDO() {
    }

    public RolePermissionDO(String roleCode, String code) {
        this.roleCode = roleCode;
        this.code = code;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
