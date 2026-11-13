package com.foundation.starter.security.permission;

import com.foundation.starter.security.principal.Principal;

/**
 * 拒绝审计落点（F-07 §3、§6）：{@link PermissionAspect} 判定无权限时**以独立事务**写一条
 * {@code DENIED} 审计（ADR-029 第 8 条）。
 *
 * <p>契约放在 security，实现由 {@code foundation-starter-audit} 提供（starter 之间只依赖 core，
 * audit 依赖 security 是 S15 §5.2 允许的唯一例外）；未引入 audit 时退化为空实现。
 */
@FunctionalInterface
public interface DeniedAuditSink {

    /** 记录一次拒绝。 */
    void denied(Principal principal, String action, String resource, String reason);

    /** 空实现（未引入 audit starter 时）。 */
    DeniedAuditSink NOOP = (principal, action, resource, reason) -> {
    };
}
