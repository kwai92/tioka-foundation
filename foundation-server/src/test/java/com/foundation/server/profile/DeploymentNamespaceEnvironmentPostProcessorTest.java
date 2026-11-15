package com.foundation.server.profile;

import com.foundation.starter.core.namespace.DeploymentNamespace;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.SpringApplication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ADP-07：部署命名空间属性源注入与显式覆盖语义。 */
class DeploymentNamespaceEnvironmentPostProcessorTest {

    private final DeploymentNamespaceEnvironmentPostProcessor processor =
            new DeploymentNamespaceEnvironmentPostProcessor();

    private static StandardEnvironment env(Map<String, Object> values) {
        StandardEnvironment environment = new StandardEnvironment();
        if (!values.isEmpty()) {
            // 模拟 application.yml / 环境变量等高优先级属性源
            environment.getPropertySources().addFirst(new MapPropertySource("testConfig", values));
        }
        return environment;
    }

    private static void process(StandardEnvironment environment) {
        new DeploymentNamespaceEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(Object.class));
    }

    @Test
    void 缺省部署解析为历史字面量() {
        StandardEnvironment environment = env(Map.of());
        process(environment);

        assertEquals("default", environment.getProperty("foundation.namespace.deployment-id"));
        assertEquals("enterprise", environment.getProperty("foundation.namespace.realm"));
        assertEquals("fdn-events", environment.getProperty("foundation.namespace.event-topic"));
        assertEquals("fdn:", environment.getProperty("foundation.namespace.key-prefix"));
        assertEquals("http://localhost:8180/realms/enterprise",
                environment.getProperty("foundation.namespace.issuer"));
    }

    @Test
    void 非缺省部署注入全套派生值() {
        StandardEnvironment environment = env(Map.of(
                DeploymentNamespace.PROPERTY, "prod-a",
                "foundation.keycloak.server-url", "http://kc:8180"));
        process(environment);

        assertEquals("enterprise-prod-a", environment.getProperty("foundation.namespace.realm"));
        assertEquals("fdn-events-prod-a", environment.getProperty("foundation.namespace.event-topic"));
        assertEquals("fdn:prod-a:", environment.getProperty("foundation.namespace.key-prefix"));
        assertEquals("fdn-prod-a-app_foundation", environment.getProperty("foundation.namespace.bucket"));
        assertEquals("foundation-prod-a", environment.getProperty("foundation.namespace.db-schema"));
        assertEquals("http://kc:8180/realms/enterprise-prod-a",
                environment.getProperty("foundation.namespace.issuer"));
        assertEquals("http://kc:8180/realms/enterprise-prod-a/protocol/openid-connect/certs",
                environment.getProperty("foundation.namespace.jwk-set-uri"));
    }

    @Test
    void 显式配置优先于派生默认值() {
        StandardEnvironment environment = env(Map.of(
                DeploymentNamespace.PROPERTY, "prod-a",
                "foundation.namespace.realm", "my-own-realm"));
        process(environment);

        // addLast 注入的默认属性源不改变更高优先级的显式值
        assertEquals("my-own-realm", environment.getProperty("foundation.namespace.realm"));
        assertEquals("fdn-events-prod-a", environment.getProperty("foundation.namespace.event-topic"));
    }

    @Test
    void 重复调用不堆积属性源() {
        StandardEnvironment environment = env(Map.of(DeploymentNamespace.PROPERTY, "uat"));
        int before = environment.getPropertySources().size();

        process(environment);
        process(environment);

        assertEquals(before + 1, environment.getPropertySources().size());
        assertNotNull(environment.getPropertySources().get(
                DeploymentNamespaceEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }

    @Test
    void 非法部署标识在启动阶段快速失败() {
        StandardEnvironment environment = env(Map.of(DeploymentNamespace.PROPERTY, "Prod_A"));

        assertThrows(IllegalArgumentException.class, () -> process(environment));
    }

    @Test
    void 处理器顺序早于装配profile() {
        assertEquals(new FoundationServerProfileEnvironmentPostProcessor().getOrder() - 10,
                processor.getOrder());
    }
}
