package com.foundation.starter.audit.report;

import java.util.List;

/**
 * 审计投递通道（F-07 §6）：把一批审计事件送到 Foundation。
 *
 * <p>生产实现为 {@code POST /api/fdn/audit/events:batch}（要求 {@code Idempotency-Key}）；
 * 投递失败必须抛异常，由 {@link AsyncBatchAuditReporter} 重试并最终落盘。
 */
@FunctionalInterface
public interface AuditTransport {

    /** 投递一批审计；失败抛异常。 */
    void send(List<AuditEvent> batch);
}
