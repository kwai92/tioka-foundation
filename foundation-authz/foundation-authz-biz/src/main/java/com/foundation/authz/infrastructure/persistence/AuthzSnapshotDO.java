package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

import java.time.LocalDateTime;

/** 授权快照实体（{@code fdn_authz_snapshot}）；{@code body} 为 JSONB，存快照内容。 */
@TableName(value = "fdn_authz_snapshot", autoResultMap = true)
public class AuthzSnapshotDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String personId;

    private String appCode;

    private Long version;

    @TableField(value = "body", typeHandler = JsonbStringTypeHandler.class)
    private String body;

    private LocalDateTime computedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }
}
