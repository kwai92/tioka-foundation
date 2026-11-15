package com.foundation.starter.core.condition;

import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-08：模式条件注解与实际 Environment 的对应关系，纯配置上下文、不需要中间件。 */
class FoundationRuntimeModeConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RemoteProbe.class, StandaloneProbe.class);

    @Test
    void 缺省模式只装配中台实现() {
        runner.run(context -> {
            assertTrue(context.containsBean("remoteBean"));
            assertFalse(context.containsBean("standaloneBean"));
        });
    }

    @Test
    void 显式foundation模式与缺省一致() {
        runner.withPropertyValues("foundation.client.mode=foundation")
                .run(context -> {
                    assertTrue(context.containsBean("remoteBean"));
                    assertFalse(context.containsBean("standaloneBean"));
                });
    }

    @Test
    void 本地模式只装配独立实现() {
        runner.withPropertyValues("foundation.client.mode=local")
                .run(context -> {
                    assertFalse(context.containsBean("remoteBean"));
                    assertTrue(context.containsBean("standaloneBean"));
                });
    }

    @Test
    void 自管模式只装配独立实现_下划线写法同样生效() {
        runner.withPropertyValues("foundation.client.mode=SELF_MANAGED")
                .run(context -> {
                    assertFalse(context.containsBean("remoteBean"));
                    assertTrue(context.containsBean("standaloneBean"));
                });
    }

    @Test
    void 非法模式导致启动失败而不是静默回落到中台模式() {
        runner.withPropertyValues("foundation.client.mode=hybrid")
                .run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnFoundationRuntimeMode(FoundationRuntimeMode.FOUNDATION)
    static class RemoteProbe {

        @Bean
        String remoteBean() {
            return "remote";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnFoundationRuntimeMode({FoundationRuntimeMode.LOCAL, FoundationRuntimeMode.SELF_MANAGED})
    static class StandaloneProbe {

        @Bean
        String standaloneBean() {
            return "standalone";
        }
    }
}
