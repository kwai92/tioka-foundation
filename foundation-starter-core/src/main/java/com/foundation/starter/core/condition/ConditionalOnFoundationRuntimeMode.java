package com.foundation.starter.core.condition;

import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按 {@code foundation.client.mode} 决定是否装配（ADP-08）。
 *
 * <p>与 {@link ConditionalOnFoundationCapability} 叠加使用：前者决定「这个 starter 开不开」，
 * 本注解决定「当前运行模式下该用哪一套实现」。两者同时标注时为 AND 关系。
 *
 * <p>未配置模式时按 {@link FoundationRuntimeMode#FOUNDATION} 判定，因此只标
 * {@code @ConditionalOnFoundationRuntimeMode(FOUNDATION)} 的自动配置对存量项目零改动。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(FoundationRuntimeModeCondition.class)
public @interface ConditionalOnFoundationRuntimeMode {

    /** 允许装配的运行模式；命中任一即装配。 */
    FoundationRuntimeMode[] value();
}
