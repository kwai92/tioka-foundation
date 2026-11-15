package com.foundation.starter.core.namespace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-07：部署命名空间派生规则与兼容基线。 */
class DeploymentNamespaceTest {

    @Test
    void 缺省部署逐字保持历史字面量() {
        DeploymentNamespace ns = DeploymentNamespace.of(null);

        assertTrue(ns.isDefault());
        assertEquals("default", ns.id());
        assertEquals("enterprise", ns.realm());
        assertEquals("fdn-events", ns.eventTopic());
        assertEquals("fdn:", ns.keyPrefix());
        assertEquals("fdn-", ns.bucketPrefix());
        assertEquals("fdn-app_foundation", ns.bucket());
        assertEquals("foundation", ns.dbSchema());
    }

    @Test
    void 空白与显式default等价() {
        assertEquals(DeploymentNamespace.defaults().realm(), DeploymentNamespace.of("  ").realm());
        assertEquals(DeploymentNamespace.defaults().keyPrefix(),
                DeploymentNamespace.of(DeploymentNamespace.DEFAULT_ID).keyPrefix());
    }

    @Test
    void 非缺省部署追加隔离段() {
        DeploymentNamespace ns = DeploymentNamespace.of("prod-a");

        assertFalse(ns.isDefault());
        assertEquals("enterprise-prod-a", ns.realm());
        assertEquals("fdn-events-prod-a", ns.eventTopic());
        assertEquals("fdn:prod-a:", ns.keyPrefix());
        assertEquals("fdn-prod-a-", ns.bucketPrefix());
        assertEquals("fdn-prod-a-app_foundation", ns.bucket());
        assertEquals("foundation-prod-a", ns.dbSchema());
    }

    @Test
    void 两套部署的资源名互不干扰() {
        DeploymentNamespace a = DeploymentNamespace.of("uat");
        DeploymentNamespace b = DeploymentNamespace.of("prod");

        assertNotEquals(a.realm(), b.realm());
        assertNotEquals(a.eventTopic(), b.eventTopic());
        assertNotEquals(a.keyPrefix(), b.keyPrefix());
        assertNotEquals(a.bucket(), b.bucket());
        assertNotEquals(a.dbSchema(), b.dbSchema());
        // 键前缀必须是「包含关系」而非「前缀冲突」：fdn:uat: 与 fdn:prod: 不会互相命中
        assertFalse(a.keyPrefix().startsWith(b.keyPrefix()));
        assertFalse(b.keyPrefix().startsWith(a.keyPrefix()));
    }

    @Test
    void realm与issuer与jwks同源() {
        DeploymentNamespace ns = DeploymentNamespace.of("prod-a");
        String url = "http://keycloak:8180/";

        assertEquals("http://keycloak:8180/realms/enterprise-prod-a/protocol/openid-connect/certs",
                ns.jwkSetUri(url));
        assertEquals("http://keycloak:8180/realms/enterprise-prod-a", ns.issuer(url));
        assertTrue(ns.jwkSetUri(url).contains("/" + ns.realm() + "/"));
    }

    @Test
    void 通用派生对自定义基线生效() {
        assertEquals("custom", DeploymentNamespace.defaults().scoped("custom"));
        assertEquals("custom-uat2", DeploymentNamespace.of("uat2").scoped("custom"));
    }

    @Test
    void 非法部署标识快速失败() {
        for (String bad : new String[]{"Prod", "prod_a", "-prod", "prod-", "a".repeat(32), "p.q"}) {
            assertThrows(IllegalArgumentException.class, () -> DeploymentNamespace.of(bad),
                    "应拒绝：" + bad);
        }
    }

    @Test
    void 大写部署标识被拒绝而不是静默归一化() {
        // 大小写不同会派生出两套资源名；严格拒绝好过悄悄变成另一套部署
        assertThrows(IllegalArgumentException.class, () -> DeploymentNamespace.of("PROD-A"));
        assertEquals("prod-a", DeploymentNamespace.of("prod-a").id());
    }

    @Test
    void 派生默认属性键齐全且供yml引用() {
        Map<String, Object> values = DeploymentNamespace.of("prod-a")
                .defaults("http://keycloak:8180");

        assertEquals("enterprise-prod-a", values.get("foundation.namespace.realm"));
        assertEquals("fdn-events-prod-a", values.get("foundation.namespace.event-topic"));
        assertEquals("fdn:prod-a:", values.get("foundation.namespace.key-prefix"));
        assertEquals("fdn-prod-a-app_foundation", values.get("foundation.namespace.bucket"));
        assertEquals("foundation-prod-a", values.get("foundation.namespace.db-schema"));
        assertEquals("prod-a", values.get("foundation.namespace.deployment-id"));
        assertTrue(values.containsKey("foundation.namespace.jwk-set-uri"));
        assertTrue(values.containsKey("foundation.namespace.issuer"));
        assertTrue(values.containsKey("foundation.namespace.bucket-prefix"));
    }
}
