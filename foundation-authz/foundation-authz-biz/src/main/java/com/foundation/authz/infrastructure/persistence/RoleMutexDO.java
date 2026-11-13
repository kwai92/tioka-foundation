package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

/** 互斥角色对（{@code fdn_role_mutex}）：关联表，无审计列。 */
@TableName("fdn_role_mutex")
public class RoleMutexDO {

    private String roleCode;

    private String mutexRoleCode;

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getMutexRoleCode() {
        return mutexRoleCode;
    }

    public void setMutexRoleCode(String mutexRoleCode) {
        this.mutexRoleCode = mutexRoleCode;
    }
}
