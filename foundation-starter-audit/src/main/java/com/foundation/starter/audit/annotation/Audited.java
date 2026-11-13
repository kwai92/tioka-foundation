package com.foundation.starter.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计上报（F-07 §6）：管理类与业务关键写操作经本注解上报。
 *
 * <p>成功操作可与业务同事务；{@code DENIED} / {@code ERROR} 审计用独立事务（{@code REQUIRES_NEW}）
 * 不随业务回滚（ADR-029 第 8 条）。上报本身走异步批量队列，**不阻塞业务**。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** 动作码，如 {@code contract.publish}。 */
    String action();

    /** 资源标识，可空。 */
    String resource() default "";

    /** 是否记录成功 / 失败与结果摘要。 */
    boolean recordResult() default true;
}
