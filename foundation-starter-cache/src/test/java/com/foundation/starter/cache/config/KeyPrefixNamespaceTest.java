package com.foundation.starter.cache.config;

import com.foundation.starter.cache.key.KeyPrefix;
import com.foundation.starter.core.config.CoreAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ADP-07：接入侧键前缀随 deployment-id 派生，且显式配置优先。 */
class KeyPrefixNamespaceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CoreAutoConfiguration.class, FoundationCacheAutoConfiguration.class));

    @Test
    void 未配置部署标识时保持历史前缀() {
        runner.run(context -> assertEquals("fdn:", context.getBean(KeyPrefix.class).raw()));
    }

    @Test
    void 部署标识自动带进键前缀() {
        runner.withPropertyValues("foundation.deployment-id=prod-a")
                .run(context -> assertEquals("fdn:prod-a:", context.getBean(KeyPrefix.class).raw()));
    }

    @Test
    void 显式键前缀不被命名空间覆盖() {
        runner.withPropertyValues(
                        "foundation.deployment-id=prod-a",
                        "foundation.cache.key-prefix=myapp:")
                .run(context -> assertEquals("myapp:", context.getBean(KeyPrefix.class).raw()));
    }

    @Test
    void 单独复用cache模块化退到缺省命名空间() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FoundationCacheAutoConfiguration.class))
                .run(context -> assertEquals("fdn:", context.getBean(KeyPrefix.class).raw()));
    }
}
