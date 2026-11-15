package com.foundation.starter.core.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 能力级开关（ADP-05 / 36 号规划 §6「开关层级」）。
 *
 * <p>激活条件 = {@code foundation.enabled} 为真 <b>且</b> {@code foundation.<value>.enabled} 为真；
 * 两个键缺省都视为 true。因此：
 *
 * <ul>
 *   <li>不配置 → 能力启用（与改造前行为一致，接入方零改动）；</li>
 *   <li>{@code foundation.<value>.enabled=false} → 只关闭该能力；</li>
 *   <li>{@code foundation.enabled=false} → 全部能力关闭（总开关作快捷方式，本地模式用）；</li>
 *   <li>总开关关闭时，单个能力显式置 true 也不能覆盖。</li>
 * </ul>
 *
 * <p>放在 {@code foundation-starter-core}，因为所有 starter 都只依赖 core（S15 §5.2 依赖约束）。
 * 关闭时整块自动配置、其 Bean、健康检查项与相关路由一并不装配，不留半装配状态。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(FoundationCapabilityCondition.class)
public @interface ConditionalOnFoundationCapability {

    /** 能力名，如 {@code audit} / {@code web} / {@code mq}，组成 {@code foundation.<value>.enabled}。 */
    String value();
}
