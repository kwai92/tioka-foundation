package com.foundation.starter.core.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * {@link ConditionalOnFoundationCapability} 的判定实现（ADP-05）。
 *
 * <p>不用叠加两个 {@code @ConditionalOnProperty}：同一类型条件注解重复时的合并行为依赖
 * Spring 版本，语义不够明确。这里用单个 {@link Condition} 显式读两个键，行为可测、可预期。
 */
public class FoundationCapabilityCondition implements Condition {

    /** 接入总开关。 */
    public static final String MASTER_SWITCH = "foundation.enabled";

    /** 能力开关键模板：{@code foundation.<capability>.enabled}。 */
    public static final String CAPABILITY_SWITCH_TEMPLATE = "foundation.%s.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(ConditionalOnFoundationCapability.class.getName());
        if (attributes == null) {
            return true;
        }
        Object rawCapability = attributes.get("value");
        String capability = rawCapability == null ? null : rawCapability.toString();
        if (capability == null || capability.isBlank()) {
            throw new IllegalStateException("@ConditionalOnFoundationCapability 必须指定能力名");
        }
        Environment environment = context.getEnvironment();
        boolean masterEnabled = environment.getProperty(MASTER_SWITCH, Boolean.class, Boolean.TRUE);
        boolean capabilityEnabled = environment.getProperty(
                String.format(CAPABILITY_SWITCH_TEMPLATE, capability), Boolean.class, Boolean.TRUE);
        return masterEnabled && capabilityEnabled;
    }
}
