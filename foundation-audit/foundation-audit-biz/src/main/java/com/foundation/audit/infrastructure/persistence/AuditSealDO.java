package com.foundation.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 审计链封印实体（{@code fdn_audit_seal}，F-09 BAS-10）。
 *
 * <p>不继承 {@code BaseDO}：审计表无行级审计与逻辑删列。
 */
@TableName("fdn_audit_seal")
public class AuditSealDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private LocalDateTime windowFrom;

    private LocalDateTime windowTo;

    private String prevHash;

    private String batchHash;

    private LocalDateTime sealedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getWindowFrom() {
        return windowFrom;
    }

    public void setWindowFrom(LocalDateTime windowFrom) {
        this.windowFrom = windowFrom;
    }

    public LocalDateTime getWindowTo() {
        return windowTo;
    }

    public void setWindowTo(LocalDateTime windowTo) {
        this.windowTo = windowTo;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public String getBatchHash() {
        return batchHash;
    }

    public void setBatchHash(String batchHash) {
        this.batchHash = batchHash;
    }

    public LocalDateTime getSealedAt() {
        return sealedAt;
    }

    public void setSealedAt(LocalDateTime sealedAt) {
        this.sealedAt = sealedAt;
    }
}
