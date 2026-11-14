package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 模块实体（{@code fdn_module}，F-05 BAS-27 §目录）。 */
@TableName("fdn_module")
public class ModuleDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String subsystemId;

    private String code;

    private String name;

    private Integer sort;

    private String status;

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

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
