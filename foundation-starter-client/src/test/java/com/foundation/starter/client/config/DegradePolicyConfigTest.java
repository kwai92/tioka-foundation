package com.foundation.starter.client.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ADP-08：身份与授权类能力永远不允许 fail-open，即使全局开启过期兜底。 */
class DegradePolicyConfigTest {

    @Test
    void 缺省全部失败关闭() {
        FoundationClientProperties.Degrade degrade = new FoundationClientProperties().getClient().getDegrade();
        assertEquals(DegradePolicy.FAIL_CLOSED, degrade.getPolicy());
        for (String capability : List.of("dict", "param", "org", "person", "security", "authz")) {
            assertEquals(DegradePolicy.FAIL_CLOSED, degrade.policyFor(capability), capability);
        }
    }

    @Test
    void 白名单内只读能力可兜底白名单外强制拒绝() {
        FoundationClientProperties.Degrade degrade = new FoundationClientProperties().getClient().getDegrade();
        degrade.setPolicy(DegradePolicy.STALE_CACHE);

        assertEquals(DegradePolicy.STALE_CACHE, degrade.policyFor("dict"));
        assertEquals(DegradePolicy.STALE_CACHE, degrade.policyFor("person"));
        assertEquals(DegradePolicy.FAIL_CLOSED, degrade.policyFor("security"));
        assertEquals(DegradePolicy.FAIL_CLOSED, degrade.policyFor("authz"));
        assertEquals(DegradePolicy.FAIL_CLOSED, degrade.policyFor("unknown"));
    }

    @Test
    void 收窄白名单后原先允许的能力也回到失败关闭() {
        FoundationClientProperties.Degrade degrade = new FoundationClientProperties().getClient().getDegrade();
        degrade.setPolicy(DegradePolicy.STALE_CACHE);
        degrade.setCapabilities(List.of("param"));

        assertEquals(DegradePolicy.FAIL_CLOSED, degrade.policyFor("dict"));
        assertEquals(DegradePolicy.STALE_CACHE, degrade.policyFor("param"));
    }

}
