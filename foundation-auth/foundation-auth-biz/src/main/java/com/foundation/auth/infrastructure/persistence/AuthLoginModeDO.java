package com.foundation.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 登录方式实体（{@code fdn_auth_login_mode}，F-04 BAS-15 §2）。
 *
 * <p>不继承 {@code BaseDO}：本表无 {@code deleted} / 审计列，时间列为自持的
 * {@code create_at} / {@code update_at}。
 */
@TableName("fdn_auth_login_mode")
public class AuthLoginModeDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String modeCode;

    private String name;

    private Boolean enabled;

    private Integer sortOrder;

    /** 是否复杂项（引导到 Keycloak 控制台）。 */
    private Boolean complex;

    private String consoleHint;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModeCode() {
        return modeCode;
    }

    public void setModeCode(String modeCode) {
        this.modeCode = modeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getComplex() {
        return complex;
    }

    public void setComplex(Boolean complex) {
        this.complex = complex;
    }

    public String getConsoleHint() {
        return consoleHint;
    }

    public void setConsoleHint(String consoleHint) {
        this.consoleHint = consoleHint;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }
}
