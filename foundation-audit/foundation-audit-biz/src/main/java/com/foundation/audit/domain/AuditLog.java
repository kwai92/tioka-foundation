package com.foundation.audit.domain;

import com.foundation.audit.api.ActorType;
import com.foundation.audit.api.AuditOutcome;
import com.foundation.audit.api.AuditReportItem;
import com.foundation.audit.api.RetentionClass;
import com.foundation.common.id.Ids;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计行（F-09 BAS-10 §数据模型；ADR-072 §1）。
 *
 * @param id             主键（{@code adl_…}）
 * @param appCode        应用编码
 * @param action         动作码
 * @param resourceType   资源类型，可空
 * @param resourceId     资源 ID，可空
 * @param outcome        结果
 * @param reason         原因 / 说明，可空
 * @param actorType      主体类型
 * @param actorId        主体 ID
 * @param runAs          代跑身份，可空
 * @param clientIp       客户端 IP，可空
 * @param userAgent      User-Agent，可空
 * @param origin         来源，可空
 * @param traceId        链路 ID，可空
 * @param requestId      请求 ID
 * @param subject        数据主体，可空
 * @param dataLabel      数据密级，可空
 * @param retentionClass 保留类别
 * @param details        明细（结构化），可空
 * @param ts             发生时刻（UTC）
 */
public record AuditLog(String id,
                       String appCode,
                       String action,
                       String resourceType,
                       String resourceId,
                       AuditOutcome outcome,
                       String reason,
                       ActorType actorType,
                       String actorId,
                       String runAs,
                       String clientIp,
                       String userAgent,
                       String origin,
                       String traceId,
                       String requestId,
                       String subject,
                       String dataLabel,
                       RetentionClass retentionClass,
                       Map<String, Object> details,
                       Instant ts) {

    /**
     * 由应用侧上报条目构造审计行（F-09 BAS-10 §上报）。
     *
     * <p>{@code resource} 按第一个 {@code :} 拆为「类型 / ID」；无冒号时类型为空、ID 为原值。
     */
    public static AuditLog fromReport(AuditReportItem item) {
        String id = Ids.newId(AuditIdPrefix.AUDIT);
        boolean hasUser = item.userId() != null && !item.userId().isBlank();
        boolean hasPerson = item.personId() != null && !item.personId().isBlank();
        ActorType actorType = (hasUser || hasPerson) ? ActorType.USER : ActorType.SYSTEM;
        String actorId = hasUser ? item.userId() : (hasPerson ? item.personId() : "unknown");

        String resourceType = null;
        String resourceId = null;
        String resource = item.resource();
        if (resource != null && !resource.isBlank()) {
            int idx = resource.indexOf(':');
            if (idx >= 0) {
                resourceType = resource.substring(0, idx);
                resourceId = resource.substring(idx + 1);
            } else {
                resourceId = resource;
            }
        }

        String requestId;
        if (item.traceId() != null && !item.traceId().isBlank()) {
            requestId = item.traceId();
        } else if (item.eventId() != null && !item.eventId().isBlank()) {
            requestId = item.eventId();
        } else {
            requestId = id;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        if (item.detail() != null) {
            details.put("detail", item.detail());
        }
        details.put("durationMillis", item.durationMillis());
        if (item.eventId() != null) {
            details.put("eventId", item.eventId());
        }

        return new AuditLog(id, item.appCode(), item.action(), resourceType, resourceId,
                AuditOutcome.of(item.result()), null, actorType, actorId, null, null, null, null, null,
                requestId, null, null, RetentionClass.SEC, details,
                item.occurredAt() != null ? item.occurredAt() : Instant.now());
    }

    /**
     * 由 Foundation 自身写入的审计行（导出、归档等运维动作）。
     *
     * <p>{@code actorId} 为空按 {@code SYSTEM} 记，否则按 {@code USER} 记；结果恒为 {@code SUCCESS}。
     */
    public static AuditLog self(String appCode, String action, String actorId, String subject,
                                RetentionClass retentionClass, Map<String, Object> details) {
        boolean hasActor = actorId != null && !actorId.isBlank();
        return new AuditLog(Ids.newId(AuditIdPrefix.AUDIT), appCode, action, null, null,
                AuditOutcome.SUCCESS, null, hasActor ? ActorType.USER : ActorType.SYSTEM,
                hasActor ? actorId : "system", null, null, null, null, null, Ids.newId("req"),
                subject, null, retentionClass, details, Instant.now());
    }
}
