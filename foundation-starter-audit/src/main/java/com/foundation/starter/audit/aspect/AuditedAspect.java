package com.foundation.starter.audit.aspect;

import com.foundation.common.id.Ids;
import com.foundation.starter.audit.annotation.Audited;
import com.foundation.starter.audit.report.AuditEvent;
import com.foundation.starter.audit.report.AuditReporter;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.context.RequestContext;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.time.Clock;
import java.time.Instant;

/**
 * {@link Audited} 拦截（F-07 §6）：记录成功 / 失败与结果摘要，随后交 {@link AuditReporter} 上报。
 *
 * <p>上报为异步批量，**不阻塞也不参与业务事务**；审计写入失败不影响业务结果。
 */
@Aspect
public class AuditedAspect {

    private final AuditReporter reporter;
    private final FoundationProperties foundationProperties;
    private final Clock clock;

    public AuditedAspect(AuditReporter reporter, FoundationProperties foundationProperties) {
        this(reporter, foundationProperties, Clock.systemUTC());
    }

    public AuditedAspect(AuditReporter reporter, FoundationProperties foundationProperties, Clock clock) {
        this.reporter = reporter;
        this.foundationProperties = foundationProperties;
        this.clock = clock;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            record(audited, AuditEvent.RESULT_SUCCESS, null, startedAt);
            return result;
        } catch (Throwable ex) {
            record(audited, AuditEvent.RESULT_FAILURE, ex.getMessage(), startedAt);
            throw ex;
        }
    }

    private void record(Audited audited, String result, String detail, long startedAtNanos) {
        Principal principal = PrincipalHolder.current().orElse(null);
        AuditEvent event = new AuditEvent(
                Ids.newId("aud"),
                Instant.now(clock),
                foundationProperties.getAppCode(),
                audited.action(),
                audited.resource() == null || audited.resource().isBlank() ? null : audited.resource(),
                result,
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.personId(),
                RequestContext.traceId().orElse(null),
                audited.recordResult() ? detail : null,
                (System.nanoTime() - startedAtNanos) / 1_000_000L);
        try {
            reporter.report(event);
        } catch (RuntimeException ex) {
            // 审计上报失败不影响业务（本地队列 + 磁盘兜底由 reporter 负责）
            org.slf4j.LoggerFactory.getLogger(AuditedAspect.class)
                    .warn("审计上报失败：{}", event.action(), ex);
        }
    }
}
