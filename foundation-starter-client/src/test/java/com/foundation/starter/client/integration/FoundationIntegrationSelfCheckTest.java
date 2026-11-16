package com.foundation.starter.client.integration;

import com.foundation.starter.client.authz.AuthorizationSnapshotClient;
import com.foundation.starter.core.config.FoundationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Set;
import java.util.function.Predicate;

import static com.foundation.starter.client.integration.IntegrationSelfCheckReport.Status.FAIL;
import static com.foundation.starter.client.integration.IntegrationSelfCheckReport.Status.PASS;
import static com.foundation.starter.client.integration.IntegrationSelfCheckReport.Status.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 平台接入自检器的纯单元测试：不需要 Docker、网络或 Spring 上下文。 */
class FoundationIntegrationSelfCheckTest {

    private static final Predicate<Set<String>> SAFE_DECISION = permissions -> permissions != null;

    @Test
    void 合法接入时六项检查全部通过() {
        IntegrationSelfCheckReport report = selfCheck(properties(), person -> Set.of("agent:read"), p -> {
        }).run("person-001", SAFE_DECISION);

        assertTrue(report.allPassed(), () -> "不应有失败项：" + report.failures());
        assertEquals(6, report.checks().size());
        assertEquals(6, report.count(PASS));
        assertEquals(0, report.count(WARN));
    }

    @Test
    void 应用编码未登记或非法时自检失败() {
        FoundationProperties missing = properties();
        missing.setAppCode(null);
        assertEquals(FAIL, status(selfCheck(missing, p -> Set.of(), null).run(null, null), "app-code"));

        FoundationProperties illegal = properties();
        illegal.setAppCode("Tioka_AI");
        assertEquals(FAIL, status(selfCheck(illegal, p -> Set.of(), null).run(null, null), "app-code"));
    }

    @Test
    void 基座地址非法时自检失败() {
        FoundationProperties props = properties();
        props.setBaseUrl("localhost:8080");
        assertEquals(FAIL, status(selfCheck(props, p -> Set.of(), null).run(null, null), "base-url"));
    }

    @Test
    void 服务账号凭据不齐是警告而非失败() {
        FoundationProperties props = properties();
        props.getServiceAccount().setClientSecret(null);
        IntegrationSelfCheckReport report = selfCheck(props, person -> Set.of(), null).run(null, null);

        assertEquals(WARN, status(report, "service-account"));
        assertTrue(report.allPassed(), "网关注入令牌的部署形态不应被判为接入失败");
    }

    @Test
    void 快照返回null时自检失败() {
        IntegrationSelfCheckReport report = selfCheck(properties(), person -> null, null)
                .run("person-001", SAFE_DECISION);

        assertEquals(FAIL, status(report, "snapshot"));
        assertFalse(report.allPassed());
    }

    @Test
    void 快照往返抛异常时自检失败() {
        AuthorizationSnapshotClient broken = person -> {
            throw new IllegalStateException("foundation unavailable");
        };
        IntegrationSelfCheckReport report = selfCheck(properties(), broken, null)
                .run("person-001", SAFE_DECISION);

        assertEquals(FAIL, status(report, "snapshot"));
        assertTrue(report.failures().get(0).detail().contains("IllegalStateException"));
    }

    @Test
    void 底座客户端未装配时自检失败() {
        IntegrationSelfCheckReport report = selfCheck(properties(), null, null)
                .run("person-001", SAFE_DECISION);

        assertEquals(FAIL, status(report, "snapshot"));
    }

    @Test
    void 判定器在数据源不可用时放行则自检失败() {
        IntegrationSelfCheckReport report = selfCheck(properties(), person -> Set.of(), null)
                .run(null, permissions -> true);

        assertEquals(FAIL, status(report, "fail-closed"));
        assertFalse(report.allPassed());
    }

    @Test
    void 判定器以异常拒绝也算不放行() {
        IntegrationSelfCheckReport report = selfCheck(properties(), person -> Set.of(), null)
                .run(null, permissions -> {
                    throw new IllegalStateException("deny");
                });

        assertEquals(PASS, status(report, "fail-closed"));
    }

    @Test
    void 未注册失效钩子是警告_注册后通过() {
        assertEquals(WARN, status(selfCheck(properties(), person -> Set.of(), null)
                .run(null, null), "snapshot-invalidation"));
        assertEquals(PASS, status(selfCheck(properties(), person -> Set.of(), person -> {
        }).run(null, null), "snapshot-invalidation"));
    }

    @Test
    void 不提供探针时两项为警告且不影响接入结论() {
        IntegrationSelfCheckReport report = selfCheck(properties(), person -> Set.of(), null)
                .run(null, null);

        assertEquals(WARN, status(report, "snapshot"));
        assertEquals(WARN, status(report, "fail-closed"));
        assertTrue(report.allPassed());
    }

    @Test
    void 应用编码登记规则边界() {
        assertTrue(FoundationIntegrationSelfCheck.isValidAppCode("tioka-ai"));
        assertTrue(FoundationIntegrationSelfCheck.isValidAppCode("ab"));
        assertFalse(FoundationIntegrationSelfCheck.isValidAppCode("a"));
        assertFalse(FoundationIntegrationSelfCheck.isValidAppCode("Tioka"));
        assertFalse(FoundationIntegrationSelfCheck.isValidAppCode("tioka_ai"));
        assertFalse(FoundationIntegrationSelfCheck.isValidAppCode("tioka.ai"));
        assertTrue(FoundationIntegrationSelfCheck.isValidAppCode("a" + "b".repeat(31)));
        assertFalse(FoundationIntegrationSelfCheck.isValidAppCode("a" + "b".repeat(32)));
    }

    private static FoundationProperties properties() {
        FoundationProperties properties = new FoundationProperties();
        properties.setAppCode("tioka-ai");
        properties.setBaseUrl("http://127.0.0.1:9999");
        properties.getServiceAccount().setClientId("client-id");
        properties.getServiceAccount().setClientSecret("client-secret");
        properties.getServiceAccount().setTokenUri("http://127.0.0.1:9999/token");
        return properties;
    }

    private static FoundationIntegrationSelfCheck selfCheck(FoundationProperties properties,
                                                            AuthorizationSnapshotClient client,
                                                            SnapshotInvalidationHook hook) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        if (client != null) {
            beans.registerSingleton("snapshotClient", client);
        }
        if (hook != null) {
            beans.registerSingleton("invalidationHook", hook);
        }
        ObjectProvider<AuthorizationSnapshotClient> clients =
                beans.getBeanProvider(AuthorizationSnapshotClient.class);
        ObjectProvider<SnapshotInvalidationHook> hooks =
                beans.getBeanProvider(SnapshotInvalidationHook.class);
        return new FoundationIntegrationSelfCheck(properties, clients, hooks);
    }

    private static IntegrationSelfCheckReport.Status status(IntegrationSelfCheckReport report, String name) {
        IntegrationSelfCheckReport.Check found = report.check(name);
        assertNotNull(found, "自检报告缺少检查项：" + name);
        return found.status();
    }
}
