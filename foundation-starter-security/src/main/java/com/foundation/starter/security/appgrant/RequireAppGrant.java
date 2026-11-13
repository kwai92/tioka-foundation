package com.foundation.starter.security.appgrant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 应用间授权校验（F-07 §9，**接口占位**）。
 *
 * <p>被调侧标注本注解，校验撤销、调用方是否登记、授权关系是否生效；OBO 时叠加
 * 「用户权限 ∩ 调用方被授予能力」收窄；不满足返回 403 并上报审计。
 *
 * <p><b>实现归 BAS-30 / F-12</b>（ADR-076、S15 §22）；本包只登记接入点与注解形态。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAppGrant {

    /** 被调应用编码。 */
    String appCode();

    /** 需要的权限码。 */
    String permission();
}
