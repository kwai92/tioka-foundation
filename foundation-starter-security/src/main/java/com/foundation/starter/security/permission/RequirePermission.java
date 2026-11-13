package com.foundation.starter.security.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验（F-07 §3）：可标方法或类；**多个值为「全部需要」**。
 *
 * <p>权限码取自授权快照的 {@code permissions} 集合；格式沿用 S15 §11.1 的 {@code 资源:动作}
 * （如 {@code contract:read}、{@code audit:read}）。不满足抛 {@code FORBIDDEN(40300)} → HTTP 403。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 需要的权限码；多个表示全部需要。 */
    String[] value();
}
