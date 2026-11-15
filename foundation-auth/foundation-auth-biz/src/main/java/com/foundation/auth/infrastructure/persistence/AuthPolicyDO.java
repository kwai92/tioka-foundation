package com.foundation.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 登录策略实体（{@code fdn_auth_policy}，F-04 BAS-15 §2）。
 *
 * <p>不继承 {@code BaseDO}：本表无 {@code deleted} / 审计列，时间列为自持的
 * {@code create_at} / {@code update_at}（与 BAS-18 {@code fdn_calendar} 同口径）。
 */
@TableName("fdn_auth_policy")
public class AuthPolicyDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String passwordPolicy;

    private Integer sessionIdleMinutes;

    private Integer sessionMaxHours;

    private Integer concurrentSessions;

    /** 强制 TOTP 的角色编码，逗号分隔。 */
    private String totpRequiredRoles;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(String passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public Integer getSessionIdleMinutes() {
        return sessionIdleMinutes;
    }

    public void setSessionIdleMinutes(Integer sessionIdleMinutes) {
        this.sessionIdleMinutes = sessionIdleMinutes;
    }

    public Integer getSessionMaxHours() {
        return sessionMaxHours;
    }

    public void setSessionMaxHours(Integer sessionMaxHours) {
        this.sessionMaxHours = sessionMaxHours;
    }

    public Integer getConcurrentSessions() {
        return concurrentSessions;
    }

    public void setConcurrentSessions(Integer concurrentSessions) {
        this.concurrentSessions = concurrentSessions;
    }

    public String getTotpRequiredRoles() {
        return totpRequiredRoles;
    }

    public void setTotpRequiredRoles(String totpRequiredRoles) {
        this.totpRequiredRoles = totpRequiredRoles;
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
