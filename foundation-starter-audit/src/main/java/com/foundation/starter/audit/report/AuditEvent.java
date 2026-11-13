package com.foundation.starter.audit.report;

import java.time.Instant;

/**
 * 审计事件（F-07 §6）：字段口径沿用 ADR-017 与 ADR-029 第 8 条；**格式只汇聚不改**。
 *
 * @param eventId         事件 ID（{@code aud_{ulid}}）
 * @param occurredAt      发生时刻（UTC）
 * @param appCode         应用编码
 * @param action          动作码
 * @param resource        资源标识，可空
 * @param result          {@code SUCCESS} / {@code FAILURE} / {@code DENIED} / {@code ERROR}
 * @param userId          应用内用户 ID，可空
 * @param personId        Foundation 人员 ID，可空
 * @param traceId         链路 ID，可空
 * @param detail          结果摘要 / 异常信息，可空
 * @param durationMillis  耗时（毫秒）
 */
public record AuditEvent(String eventId,
                         Instant occurredAt,
                         String appCode,
                         String action,
                         String resource,
                         String result,
                         String userId,
                         String personId,
                         String traceId,
                         String detail,
                         long durationMillis) {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_ERROR = "ERROR";
}
