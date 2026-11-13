package com.foundation.starter.audit.report;

import com.foundation.common.id.Ids;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.context.RequestContext;
import com.foundation.starter.security.permission.DeniedAuditSink;
import com.foundation.starter.security.principal.Principal;

import java.time.Clock;
import java.time.Instant;

/**
 * {@link DeniedAuditSink} 的审计实现（F-07 §3、§6）：无权限拒绝时写一条 {@code DENIED} 审计。
 *
 * <p>经 {@link AuditReporter} 异步上报，**不参与业务事务**（ADR-029 第 8 条要求的独立事务语义）。
 */
public class AuditDeniedSink implements DeniedAuditSink {

    private final AuditReporter reporter;
    private final FoundationProperties foundationProperties;
    private final Clock clock;

    public AuditDeniedSink(AuditReporter reporter, FoundationProperties foundationProperties) {
        this(reporter, foundationProperties, Clock.systemUTC());
    }

    public AuditDeniedSink(AuditReporter reporter, FoundationProperties foundationProperties, Clock clock) {
        this.reporter = reporter;
        this.foundationProperties = foundationProperties;
        this.clock = clock;
    }

    @Override
    public void denied(Principal principal, String action, String resource, String reason) {
        reporter.report(new AuditEvent(
                Ids.newId("aud"),
                Instant.now(clock),
                foundationProperties.getAppCode(),
                action,
                resource,
                AuditEvent.RESULT_DENIED,
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.personId(),
                RequestContext.traceId().orElse(null),
                reason,
                0L));
    }
}
