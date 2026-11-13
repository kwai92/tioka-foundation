package com.foundation.starter.security.permission;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.starter.security.snapshot.AuthzSnapshot;
import com.foundation.starter.security.snapshot.AuthzSnapshotClient;
import com.foundation.starter.security.snapshot.DeptScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 权限拦截（F-07 §3、§15）：403 + DENIED、多值全部需要、@PublicApi 放行、未登录 401。 */
class PermissionAspectTest {

    private static final String PERSON = "per_1";

    private AuthzSnapshotClient snapshotClient;
    private DeniedAuditSink deniedAuditSink;
    private PermissionAspect aspect;

    @BeforeEach
    void setUp() {
        snapshotClient = mock(AuthzSnapshotClient.class);
        deniedAuditSink = mock(DeniedAuditSink.class);
        aspect = new PermissionAspect(snapshotClient, deniedAuditSink);
    }

    @AfterEach
    void tearDown() {
        PrincipalHolder.clear();
    }

    @Test
    void deniedWhenPermissionMissing() throws Throwable {
        PrincipalHolder.set(principal(), 1L);
        when(snapshotClient.load(PERSON)).thenReturn(snapshot(Set.of("other:read")));
        ProceedingJoinPoint joinPoint = joinPoint("read");

        Throwable thrown = catchThrowable(() -> aspect.check(joinPoint));

        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(deniedAuditSink).denied(any(), eq("user:read"), anyString(), anyString());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void deniedWhenSnapshotMissing() throws Throwable {
        PrincipalHolder.set(principal(), 1L);
        when(snapshotClient.load(PERSON)).thenReturn(null);
        ProceedingJoinPoint joinPoint = joinPoint("read");

        Throwable thrown = catchThrowable(() -> aspect.check(joinPoint));

        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void requiresAllDeclaredPermissions() throws Throwable {
        PrincipalHolder.set(principal(), 1L);
        when(snapshotClient.load(PERSON)).thenReturn(snapshot(Set.of("user:read")));
        ProceedingJoinPoint joinPoint = joinPoint("write");

        Throwable thrown = catchThrowable(() -> aspect.check(joinPoint));

        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(deniedAuditSink).denied(any(), eq("user:read,user:write"), anyString(), anyString());
    }

    @Test
    void allowsWhenAllDeclaredPermissionsPresent() throws Throwable {
        PrincipalHolder.set(principal(), 1L);
        when(snapshotClient.load(PERSON)).thenReturn(snapshot(Set.of("user:read", "user:write")));
        ProceedingJoinPoint joinPoint = joinPoint("write");

        Object result = aspect.check(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void publicApiPassesWithoutLogin() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("health");

        Object result = aspect.check(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(snapshotClient, never()).load(anyString());
        verify(deniedAuditSink, never()).denied(any(), anyString(), anyString(), anyString());
    }

    @Test
    void unauthorizedWhenNotLoggedIn() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("read");

        Throwable thrown = catchThrowable(() -> aspect.check(joinPoint));

        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(deniedAuditSink).denied(isNull(), eq("user:read"), anyString(), anyString());
        verify(joinPoint, never()).proceed();
    }

    private ProceedingJoinPoint joinPoint(String methodName) throws Throwable {
        SampleApi target = new SampleApi();
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(SampleApi.class.getDeclaredMethod(methodName));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenReturn("ok");
        return joinPoint;
    }

    private static Principal principal() {
        return new Principal("1", PERSON, "acc_1", "E1001", "张三", "ou_root", "tioka");
    }

    private static AuthzSnapshot snapshot(Set<String> permissions) {
        return new AuthzSnapshot(PERSON, "tioka", 1L, Set.of(), permissions,
                List.of(), DeptScope.ofAll(), Map.of(), null);
    }

    /** 测试用接入点。 */
    static class SampleApi {

        @RequirePermission("user:read")
        String read() {
            return "ok";
        }

        @RequirePermission({"user:read", "user:write"})
        String write() {
            return "ok";
        }

        @PublicApi
        String health() {
            return "ok";
        }
    }
}
