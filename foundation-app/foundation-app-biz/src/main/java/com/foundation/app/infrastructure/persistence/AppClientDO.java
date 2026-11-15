package com.foundation.app.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.MutableRecordDO;

import java.time.LocalDateTime;

/** 应用客户端实体（{@code fdn_app_client}，F-05 BAS-04 §迁移 V3；无 {@code deleted} 列）。 */
@TableName("fdn_app_client")
public class AppClientDO extends MutableRecordDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String appId;

    private String clientId;

    /** 回调地址，逗号分隔文本。 */
    private String redirectUris;

    /** 授权类型，逗号分隔文本。 */
    private String grantTypes;

    private String status;

    /** 密钥轮换时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    private LocalDateTime secretRotatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(String grantTypes) {
        this.grantTypes = grantTypes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSecretRotatedAt() {
        return secretRotatedAt;
    }

    public void setSecretRotatedAt(LocalDateTime secretRotatedAt) {
        this.secretRotatedAt = secretRotatedAt;
    }
}
