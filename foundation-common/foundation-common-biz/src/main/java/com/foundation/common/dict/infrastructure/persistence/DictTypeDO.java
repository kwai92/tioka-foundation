package com.foundation.common.dict.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.MutableRecordDO;

/** 字典类型实体（{@code fdn_dict_type}，F-02 BAS-09 §迁移 V2）。 */
@TableName("fdn_dict_type")
public class DictTypeDO extends MutableRecordDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    /** 归属应用；{@code null} 表示全局。 */
    private String appCode;

    private String status;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
