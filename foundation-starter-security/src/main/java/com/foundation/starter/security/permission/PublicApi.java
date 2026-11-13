package com.foundation.starter.security.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 免鉴权白名单（F-07 §3）：显式声明不需要权限校验的端点。
 *
 * <p>ArchUnit 规则（S15 §5.3）要求每个 Controller 方法**要么声明权限、要么标 {@code @PublicApi}**。
 * 框架级免鉴权路径另由 {@code foundation.security.public-api-paths} 声明。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
}
