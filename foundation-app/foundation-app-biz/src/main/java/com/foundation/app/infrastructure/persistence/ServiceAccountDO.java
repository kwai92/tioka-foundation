package com.foundation.app.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.MutableRecordDO;

import java.time.LocalDateTime;

/** 服务账号实体（{@code fdn_service_account}，F-05 BAS-04 §迁移 V3；无 {@code deleted} 列）。 */
@TableName("fdn_service_account")
public class ServiceAccountDO extends MutableRecordDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String accountId;

    private String appId;

    private String clientId;

    private String name;

    /** 可用权限码白名单，JSON 数组文本（列 {@code TEXT}）。 */
    private String permissionCodes;

    private String ownerUserId;

    private String status;

    /** 过期时间（UTC 语义，列 {@code TIMESTAMPTZ}）；为空表示不过期。 */
    private LocalDateTime expiresAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(String permissionCodes) {
        this.permissionCodes = permissionCodes;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
