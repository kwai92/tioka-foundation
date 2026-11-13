package com.foundation.starter.security.permission;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.starter.security.snapshot.AuthzSnapshot;
import com.foundation.starter.security.snapshot.AuthzSnapshotClient;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * 权限拦截（F-07 §3）：不满足抛 {@code FORBIDDEN(40300)} → HTTP 403，并写一条 {@code DENIED} 审计。
 *
 * <p>{@code order} 早于事务（F-07 §3），避免无权限请求进入业务事务。
 * 默认拒绝：{@code @RequirePermission} 存在但快照缺失或取不到 → 抛 {@code FORBIDDEN}。
 */
@Aspect
@Order(PermissionAspect.ORDER)
public class PermissionAspect {

    /** 早于事务通知（{@link Ordered#LOWEST_PRECEDENCE}）。 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final AuthzSnapshotClient snapshotClient;
    private final DeniedAuditSink deniedAuditSink;

    public PermissionAspect(AuthzSnapshotClient snapshotClient, DeniedAuditSink deniedAuditSink) {
        this.snapshotClient = snapshotClient;
        this.deniedAuditSink = deniedAuditSink == null ? DeniedAuditSink.NOOP : deniedAuditSink;
    }

    @Around("@annotation(com.foundation.starter.security.permission.RequirePermission)"
            + " || @within(com.foundation.starter.security.permission.RequirePermission)"
            + " || @annotation(com.foundation.starter.security.permission.PublicApi)"
            + " || @within(com.foundation.starter.security.permission.PublicApi)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() == null
                ? method.getDeclaringClass()
                : joinPoint.getTarget().getClass();

        if (AnnotatedElementUtils.hasAnnotation(method, PublicApi.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, PublicApi.class)) {
            return joinPoint.proceed();
        }

        RequirePermission required = AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequirePermission.class);
        }
        if (required == null || required.value().length == 0) {
            return joinPoint.proceed();
        }

        String resource = describe(method);
        Principal principal = PrincipalHolder.current().orElse(null);
        if (principal == null) {
            deniedAuditSink.denied(null, String.join(",", required.value()), resource, "未登录");
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        AuthzSnapshot snapshot = null;
        if (snapshotClient != null && principal.personId() != null) {
            snapshot = snapshotClient.load(principal.personId());
        }
        if (snapshot == null || !snapshot.hasAll(required.value())) {
            deniedAuditSink.denied(principal, String.join(",", required.value()), resource, "无权限");
            throw new BizException(ErrorCode.FORBIDDEN,
                    "无权限：" + String.join(",", required.value()));
        }
        return joinPoint.proceed();
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
