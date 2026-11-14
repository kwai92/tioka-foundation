package com.foundation.audit.api;

import java.time.Instant;

/**
 * 审计查询条件（F-09 BAS-10 §查询）。
 *
 * @param app          应用编码，可空
 * @param actorId      主体 ID，可空
 * @param action       动作码，可空
 * @param resourceType 资源类型，可空
 * @param resourceId   资源 ID，可空
 * @param outcome      结果，可空
 * @param from         起始时刻（含），可空
 * @param to           结束时刻（不含），可空
 * @param cursor       游标（{@code {tsEpochMilli}|{id}}），可空
 * @param size         每页行数（{@code <= 0} 取默认 20，上限 100）
 */
public record AuditQuery(String app, String actorId, String action, String resourceType, String resourceId,
                         String outcome, Instant from, Instant to, String cursor, int size) {
}
