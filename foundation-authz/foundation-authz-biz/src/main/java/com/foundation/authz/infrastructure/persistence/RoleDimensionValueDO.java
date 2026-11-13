package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 角色维度取值实体（{@code fdn_role_dimension_value}）。 */
@TableName("fdn_role_dimension_value")
public class RoleDimensionValueDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String roleCode;

    private String dimensionId;

    private String valueCode;

    private Boolean allValues;

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

    public String getDimensionId() {
        return dimensionId;
    }

    public void setDimensionId(String dimensionId) {
        this.dimensionId = dimensionId;
    }

    public String getValueCode() {
        return valueCode;
    }

    public void setValueCode(String valueCode) {
        this.valueCode = valueCode;
    }

    public Boolean getAllValues() {
        return allValues;
    }

    public void setAllValues(Boolean allValues) {
        this.allValues = allValues;
    }
}
