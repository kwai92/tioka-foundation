package com.foundation.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 审计行实体（{@code fdn_audit_log}，F-09 BAS-10）。
 *
 * <p>不继承 {@code BaseDO}：审计表无 {@code create_at}/{@code update_at}/{@code create_by}/{@code update_by}/
 * {@code deleted} 列；主键为 {@code (id, ts)}（分区表要求），此处仅声明 {@code id}。
 * {@code details} 为 JSONB，用 {@link JsonbStringTypeHandler} 存取文本。
 */
@TableName(value = "fdn_audit_log", autoResultMap = true)
public class AuditLogDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String appCode;

    private String action;

    private String resourceType;

    private String resourceId;

    private String outcome;

    private String reason;

    private String actorType;

    private String actorId;

    private String runAs;

    private String clientIp;

    private String userAgent;

    private String origin;

    private String traceId;

    private String requestId;

    private String subject;

    private String dataLabel;

    private String retentionClass;

    @TableField(value = "details", typeHandler = JsonbStringTypeHandler.class)
    private String details;

    private LocalDateTime ts;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getRunAs() {
        return runAs;
    }

    public void setRunAs(String runAs) {
        this.runAs = runAs;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDataLabel() {
        return dataLabel;
    }

    public void setDataLabel(String dataLabel) {
        this.dataLabel = dataLabel;
    }

    public String getRetentionClass() {
        return retentionClass;
    }

    public void setRetentionClass(String retentionClass) {
        this.retentionClass = retentionClass;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }
}
