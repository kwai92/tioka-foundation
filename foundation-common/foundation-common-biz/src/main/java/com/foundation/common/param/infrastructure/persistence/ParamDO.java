package com.foundation.common.param.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 参数实体（{@code fdn_param}，F-02 BAS-09 §迁移 V2）。
 *
 * <p>本表没有 {@code create_at}/{@code create_by} 列，故不继承审计基类。{@code update_at} 不加填充注解：
 * 插入时交由数据库 {@code DEFAULT now()}，更新时由仓储用 {@code setSql("update_at = now()")} 写入，
 * 与原 SQL 行为一致。
 */
@TableName("fdn_param")
public class ParamDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 归属应用；{@code null} 表示全局。 */
    private String appCode;

    private String paramKey;

    /** 落库值；{@code secret=true} 时为 AES-256-GCM 密文。 */
    private String paramValue;

    private String description;

    private Boolean secret;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 更新时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    private LocalDateTime updateAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getSecret() {
        return secret;
    }

    public void setSecret(Boolean secret) {
        this.secret = secret;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }
}
