package com.foundation.example;

import com.foundation.starter.cache.config.FoundationCacheAutoConfiguration;
import com.foundation.starter.cache.key.KeyPrefix;
import com.foundation.starter.cache.lock.DistributedLock;
import com.foundation.starter.cache.lock.LocalDistributedLock;
import com.foundation.starter.cache.ratelimit.LocalRateLimiter;
import com.foundation.starter.cache.ratelimit.RateLimiter;
import com.foundation.starter.core.config.CoreAutoConfiguration;
import com.foundation.starter.core.namespace.DeploymentNamespace;
import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import com.foundation.starter.mq.config.FoundationMqAutoConfiguration;
import com.foundation.starter.mq.mq.LoggingMqTemplate;
import com.foundation.starter.mq.mq.MqTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADP-06：无 Redis、无 RocketMQ、无 PostgreSQL、无 Keycloak 的接入形态自检。
 *
 * <p>对应 36 号规划 §7 的 {@code GaReplaceSpiGateIT} 中可自动化、无需 Docker 的部分：
 * 只装配 core / cache / mq 三个 starter 的自动配置，确认全部退化为本地实现，
 * 并且部署命名空间能自动带进键前缀。不启动 Web 容器、不连数据库、不拉镜像。
 */
class StandaloneRuntimeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CoreAutoConfiguration.class,
                    FoundationCacheAutoConfiguration.class,
                    FoundationMqAutoConfiguration.class))
            .withPropertyValues(
                    "foundation.app-code=edge-tool",
                    "foundation.cache.backend=local",
                    "foundation.mq.relay.enabled=false");

    @Test
    void 自检1_不连Redis也能装配分布式锁() {
        runner.run(context -> assertInstanceOf(LocalDistributedLock.class,
                context.getBean(DistributedLock.class)));
    }

    @Test
    void 自检2_不连Redis也能装配限流器() {
        runner.run(context -> assertInstanceOf(LocalRateLimiter.class,
                context.getBean(RateLimiter.class)));
    }

    @Test
    void 自检3_不连RocketMQ也能投递事件() {
        runner.run(context -> assertInstanceOf(LoggingMqTemplate.class,
                context.getBean(MqTemplate.class)));
    }

    @Test
    void 自检4_上下文中没有Redis与RocketMQ客户端Bean() {
        runner.run(context -> {
            assertFalse(context.containsBean("foundationRedissonClient"),
                    "backend=local 不应装配 Redisson 客户端");
            assertFalse(context.containsBean("rocketMqMqTemplate"),
                    "类路径无 RocketMQTemplate 时不应装配其投递实现");
            assertFalse(context.containsBean("mybatisOutbox"),
                    "无 SqlSessionFactory 时不应装配 MyBatis outbox");
            assertTrue(context.containsBean("loggingMqTemplate"));
        });
    }

    @Test
    void 自检5_未配置部署标识时保持兼容基线() {
        runner.run(context -> {
            assertNotNull(context.getBean(DeploymentNamespace.class));
            assertEquals("fdn:", context.getBean(KeyPrefix.class).raw());
        });
    }

    @Test
    void 自检6_两套部署的键前缀互不干扰() {
        AtomicReference<String> uat = new AtomicReference<>();
        AtomicReference<String> prod = new AtomicReference<>();
        runner.withPropertyValues("foundation.deployment-id=uat2")
                .run(context -> uat.set(context.getBean(KeyPrefix.class).raw()));
        runner.withPropertyValues("foundation.deployment-id=prod2")
                .run(context -> prod.set(context.getBean(KeyPrefix.class).raw()));

        assertEquals("fdn:uat2:", uat.get());
        assertEquals("fdn:prod2:", prod.get());
        assertNotEquals(uat.get(), prod.get());
        assertFalse(uat.get().startsWith(prod.get()));
        assertFalse(prod.get().startsWith(uat.get()));
    }

    @Test
    void 自检7_运行模式由core暴露且缺省保持兼容基线() {
        runner.run(context -> assertEquals(FoundationRuntimeMode.FOUNDATION,
                context.getBean(FoundationRuntimeMode.class)));
        runner.withPropertyValues("foundation.client.mode=local")
                .run(context -> {
                    assertEquals(FoundationRuntimeMode.LOCAL,
                            context.getBean(FoundationRuntimeMode.class));
                    // 无中台构件的形态下不应出现任何指向 Foundation 的出站客户端
                    assertFalse(context.containsBean("foundationHttpClient"));
                });
    }
}
