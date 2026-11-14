package com.foundation.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

import java.time.LocalDateTime;

/**
 * 账号实体（{@code fdn_account}，F-04 §1）：账号是「人用来登录的凭证载体」。
 *
 * <p>Foundation 不保存口令；{@code keycloakUserId} 推送后回写（先落库再推送，待发时为空）。
 */
@TableName("fdn_account")
public class AccountDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String loginName;

    private String personId;

    private String keycloakUserId;

    private String status;

    private String type;

    private LocalDateTime lastLoginAt;

    private Long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
