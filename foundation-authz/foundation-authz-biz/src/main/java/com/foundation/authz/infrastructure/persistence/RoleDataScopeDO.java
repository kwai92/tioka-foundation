package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.foundation.starter.data.BaseDO;

import java.util.List;

/** 角色数据范围实体（{@code fdn_role_data_scope}）；{@code dept_units} 存 JSON 文本。 */
@TableName(value = "fdn_role_data_scope", autoResultMap = true)
public class RoleDataScopeDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String roleCode;

    private String deptScope;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> deptUnits;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getDeptScope() {
        return deptScope;
    }

    public void setDeptScope(String deptScope) {
        this.deptScope = deptScope;
    }

    public List<String> getDeptUnits() {
        return deptUnits;
    }

    public void setDeptUnits(List<String> deptUnits) {
        this.deptUnits = deptUnits;
    }
}
