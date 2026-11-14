package com.foundation.audit.api;

import java.time.Instant;

/**
 * 审计视图（F-09 BAS-10 §查询）。
 *
 * @param id             审计行 ID（{@code adl_…}）
 * @param appCode        应用编码
 * @param action         动作码
 * @param resourceType   资源类型
 * @param resourceId     资源 ID
 * @param outcome        结果
 * @param reason         原因 / 说明
 * @param actorType      主体类型
 * @param actorId        主体 ID
 * @param runAs          代跑身份
 * @param clientIp       客户端 IP
 * @param userAgent      User-Agent
 * @param origin         来源（WEB/API/…）
 * @param traceId        链路 ID
 * @param requestId      请求 ID
 * @param subject       数据主体
 * @param dataLabel      数据密级
 * @param retentionClass 保留类别
 * @param detail         明细（JSON 文本）
 * @param durationMillis 耗时（毫秒）
 * @param ts             发生时刻（UTC）
 */
public record AuditView(String id, String appCode, String action, String resourceType, String resourceId,
                        String outcome, String reason, String actorType, String actorId, String runAs,
                        String clientIp, String userAgent, String origin, String traceId, String requestId,
                        String subject, String dataLabel, String retentionClass, String detail,
                        long durationMillis, Instant ts) {
}
