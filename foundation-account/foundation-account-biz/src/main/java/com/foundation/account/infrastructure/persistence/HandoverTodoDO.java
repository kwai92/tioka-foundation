package com.foundation.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

import java.time.LocalDateTime;

/**
 * 交接待办实体（{@code fdn_handover_todo}，F-04 BAS-14 §1 第 3 步）。
 *
 * <p>{@code permission_codes} 以逗号分隔存储（权限码本身不含逗号）。
 */
@TableName("fdn_handover_todo")
public class HandoverTodoDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String ownerPersonId;

    private String accountId;

    private String serviceAccountId;

    private String appCode;

    private String name;

    private String permissionCodes;

    private String status;

    private String newOwnerPersonId;

    private LocalDateTime handledAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerPersonId() {
        return ownerPersonId;
    }

    public void setOwnerPersonId(String ownerPersonId) {
        this.ownerPersonId = ownerPersonId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getServiceAccountId() {
        return serviceAccountId;
    }

    public void setServiceAccountId(String serviceAccountId) {
        this.serviceAccountId = serviceAccountId;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNewOwnerPersonId() {
        return newOwnerPersonId;
    }

    public void setNewOwnerPersonId(String newOwnerPersonId) {
        this.newOwnerPersonId = newOwnerPersonId;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }
}
