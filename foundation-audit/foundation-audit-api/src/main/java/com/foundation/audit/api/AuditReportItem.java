package com.foundation.audit.api;

import java.time.Instant;

/**
 * 审计上报条目（F-09 BAS-10 §上报）：应用侧按「只汇聚不改」口径提交的最小事件。
 *
 * @param eventId        事件 ID（应用侧生成，用于回执与去重展示）
 * @param occurredAt     发生时刻（UTC）
 * @param appCode        应用编码
 * @param action         动作码
 * @param resource       资源标识（{@code type:id} 或裸 id），可空
 * @param result         {@code SUCCESS} / {@code FAILURE} / {@code DENIED} / {@code ERROR}
 * @param userId         应用内用户 ID，可空
 * @param personId       Foundation 人员 ID，可空
 * @param traceId        链路 ID，可空
 * @param detail         结果摘要 / 异常信息，可空
 * @param durationMillis 耗时（毫秒）
 */
public record AuditReportItem(String eventId, Instant occurredAt, String appCode, String action,
                              String resource, String result, String userId, String personId,
                              String traceId, String detail, long durationMillis) {
}
