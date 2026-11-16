package com.foundation.starter.core.condition;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-05：能力开关与总开关的组合语义，纯配置上下文，不需要任何中间件。 */
class FoundationCapabilityConditionTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ProbeConfiguration.class);

    @Test
    void 缺省时能力启用_与改造前行为一致() {
        runner.run(context -> assertTrue(context.containsBean("probeBean")));
    }

    @Test
    void 单个能力开关可只关该能力() {
        runner.withPropertyValues("foundation.probe.enabled=false")
                .run(context -> assertFalse(context.containsBean("probeBean")));
    }

    @Test
    void 其他能力的开关不影响本能力() {
        runner.withPropertyValues("foundation.other.enabled=false")
                .run(context -> assertTrue(context.containsBean("probeBean")));
    }

    @Test
    void 总开关关闭时全部能力关闭() {
        runner.withPropertyValues("foundation.enabled=false")
                .run(context -> assertFalse(context.containsBean("probeBean")));
    }

    @Test
    void 总开关优先_单能力显式开启也不能覆盖() {
        runner.withPropertyValues("foundation.enabled=false", "foundation.probe.enabled=true")
                .run(context -> assertFalse(context.containsBean("probeBean")));
    }

    @Test
    void 总开关开启且能力开启时装配() {
        runner.withPropertyValues("foundation.enabled=true", "foundation.probe.enabled=true")
                .run(context -> assertTrue(context.containsBean("probeBean")));
    }

    @Test
    void 注解属性不可读时按未满足处理_不绕过总开关() {
        ConditionContext context = Mockito.mock(ConditionContext.class);
        Mockito.when(context.getEnvironment()).thenReturn(
                new MockEnvironment().withProperty("foundation.enabled", "false"));
        // getAnnotationAttributes 返回 null（ASM 元数据等场景）：必须 fail-closed
        AnnotatedTypeMetadata metadata = Mockito.mock(AnnotatedTypeMetadata.class);
        Mockito.when(metadata.getAnnotationAttributes(
                ConditionalOnFoundationCapability.class.getName())).thenReturn(null);
        assertFalse(new FoundationCapabilityCondition().matches(context, metadata));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnFoundationCapability("probe")
    static class ProbeConfiguration {

        @Bean
        String probeBean() {
            return "probe";
        }
    }
}
