package com.foundation.starter.core.condition;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnFoundationCapability("probe")
    static class ProbeConfiguration {

        @Bean
        String probeBean() {
            return "probe";
        }
    }
}
