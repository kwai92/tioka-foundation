package com.foundation.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 上报批次回执实体（{@code fdn_audit_report}，F-09 BAS-10）。
 *
 * <p>按 {@code idempotency_key} 唯一，记录该批次当时的接收条数，实现上报幂等。
 */
@TableName("fdn_audit_report")
public class AuditReportReceiptDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String idempotencyKey;

    private String appCode;

    private Integer accepted;

    private LocalDateTime createAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public Integer getAccepted() {
        return accepted;
    }

    public void setAccepted(Integer accepted) {
        this.accepted = accepted;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }
}
