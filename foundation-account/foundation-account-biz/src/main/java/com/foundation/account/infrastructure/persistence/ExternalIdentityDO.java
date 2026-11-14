package com.foundation.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

import java.time.LocalDateTime;

/**
 * 外部身份实体（{@code fdn_external_identity}，F-04 §2）。
 *
 * <p>{@code personId} 为 {@code null} 即一条待处理关联；不允许仅凭姓名匹配、不自动建人。
 */
@TableName("fdn_external_identity")
public class ExternalIdentityDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String source;

    private String externalId;

    private String personId;

    private String linkMode;

    private LocalDateTime linkedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getLinkMode() {
        return linkMode;
    }

    public void setLinkMode(String linkMode) {
        this.linkMode = linkMode;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }
}
