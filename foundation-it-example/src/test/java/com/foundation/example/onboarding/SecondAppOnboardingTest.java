package com.foundation.example.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.authz.AuthorizationSnapshotClient;
import com.foundation.starter.client.config.FoundationClientAutoConfiguration;
import com.foundation.starter.client.config.FoundationClientProperties;
import com.foundation.starter.client.config.FoundationIntegrationAutoConfiguration;
import com.foundation.starter.client.integration.FoundationIntegrationSelfCheck;
import com.foundation.starter.client.integration.IntegrationSelfCheckReport;
import com.foundation.starter.client.integration.SnapshotInvalidationHook;
import com.foundation.starter.core.config.FoundationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二接入方验收（P0 接入平面）：证明一个新的派生应用「只加依赖 + 加配置」就能接上中台，
 * 不需要修改 Foundation 源码，也不需要复制或依赖第一个业务应用 Tioka 的任何类。
 *
 * <p>本工程刻意不引 {@code foundation-starter-security}（那是完整标准路径），
 * 只引 {@code foundation-starter-client} 这条轻量只读路径；自检用桩客户端，
 * 因此纯本地可跑，不依赖 Docker、真实中台或网络。
 */
class SecondAppOnboardingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FoundationClientAutoConfiguration.class,
                    FoundationIntegrationAutoConfiguration.class))
            .withUserConfiguration(AppConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(SnapshotInvalidationHook.class, () -> personId -> {
            })
            .withPropertyValues(
                    "foundation.client.mode=foundation",
                    "foundation.app-code=tioka-ai",
                    "foundation.base-url=http://127.0.0.1:9999",
                    "foundation.service-account.client-id=client-id",
                    "foundation.service-account.client-secret=client-secret",
                    "foundation.service-account.token-uri=http://127.0.0.1:9999/token");

    @Test
    void 只加依赖与配置即可接入且自检通过() {
        runner.withBean(AuthorizationSnapshotClient.class, () -> personId -> Set.of("agent:read"))
                .run(context -> {
            assertNull(context.getStartupFailure(), "第二接入方不应需要任何手写适配代码");
            assertNotNull(context.getBean(FoundationIntegrationSelfCheck.class));
            assertNotNull(context.getBean(AuthorizationSnapshotClient.class));

            IntegrationSelfCheckReport report = context.getBean(FoundationIntegrationSelfCheck.class)
                    .run("person-001", permissions -> permissions != null);
            assertTrue(report.allPassed(), () -> "接入自检应通过：" + report.failures());
            assertEquals(6, report.checks().size(), "平台定义六项自检，应用不得自行裁剪");
        });
    }

    @Test
    void 骨架不依赖第一个业务应用的任何类() {
        assertFalse(ClassUtils.isPresent(
                "com.tioka.framework.security.PermissionEvaluator", getClass().getClassLoader()),
                "派生应用不得反向依赖 Tioka 的 framework");
        assertFalse(ClassUtils.isPresent(
                "com.tioka.server.foundation.FoundationAuthzConfiguration", getClass().getClassLoader()),
                "派生应用不得复制 Tioka 的接入适配类");
    }

    @Test
    void 应用编码非法时平台护栏在启动期拒绝() {
        runner.withPropertyValues("foundation.app-code=Tioka_AI")
                .run(context -> {
                    assertNotNull(context.getStartupFailure(), "非法应用编码必须被平台护栏拒绝");
                    assertTrue(context.getStartupFailure().getMessage().contains("foundation.app-code"),
                            "失败信息应指向登记规则，而不是含糊的 Bean 创建错误");
                });
    }

    @Test
    void 基座地址非法时平台护栏在启动期拒绝() {
        runner.withPropertyValues("foundation.base-url=localhost:9999")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void 本地模式不装配平台接入护栏_存量应用零影响() {
        runner.withPropertyValues("foundation.client.mode=local")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(FoundationIntegrationSelfCheck.class).isEmpty());
                    assertTrue(context.getBeansOfType(com.foundation.starter.client.http.FoundationHttpClient.class)
                            .isEmpty());
                });
    }

    @Test
    void 自检器按需执行_启动期不发网络请求() {
        AtomicInteger calls = new AtomicInteger();
        runner.withBean(AuthorizationSnapshotClient.class, () -> personId -> {
            calls.incrementAndGet();
            return Set.of("agent:read");
        }).run(context -> {
            assertNull(context.getStartupFailure(), "自检是按需调用，不应挂在启动流程里");
            assertEquals(0, calls.get(), "启动期不得调用中台端点");
            context.getBean(FoundationIntegrationSelfCheck.class)
                    .run("person-001", permissions -> permissions != null);
            assertEquals(1, calls.get(), "只有显式执行自检才发起快照往返");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({FoundationProperties.class, FoundationClientProperties.class})
    static class AppConfig {
    }
}
