package com.foundation.server.profile;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.SpringApplication;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-05：装配 profile 的映射、属性注入与条件装配联动（不依赖数据库/中间件）。 */
class FoundationServerProfileTest {

    private static final FoundationServerProfileEnvironmentPostProcessor PROCESSOR =
            new FoundationServerProfileEnvironmentPostProcessor();

    private static final String PREFIX =
            FoundationServerProfileEnvironmentPostProcessor.DOMAIN_PREFIX;

    private static MockEnvironment apply(String profile) {
        MockEnvironment environment = new MockEnvironment();
        if (profile != null) {
            environment.setProperty(FoundationServerProfileEnvironmentPostProcessor.PROFILE_PROPERTY,
                    profile);
        }
        PROCESSOR.postProcessEnvironment(environment, new SpringApplication(Object.class));
        return environment;
    }

    @Test
    void 未配置profile时全量装配且行为与改造前一致() {
        MockEnvironment environment = apply(null);
        assertEquals("full", environment.getProperty(
                FoundationServerProfileEnvironmentPostProcessor.PROFILE_PROPERTY));
        for (String domain : FoundationServerProfile.ALL_DOMAINS) {
            assertEquals("true", environment.getProperty(PREFIX + domain), domain);
        }
    }

    @Test
    void minimal只装配字典参数与编号() {
        MockEnvironment environment = apply("minimal");
        assertEquals(Set.of("common", "sequence"),
                FoundationServerProfile.MINIMAL.enabledDomains());
        assertEquals("true", environment.getProperty(PREFIX + "common"));
        assertEquals("true", environment.getProperty(PREFIX + "sequence"));
        for (String domain : List.of("app", "org", "authz", "file", "notify", "sync", "scim")) {
            assertEquals("false", environment.getProperty(PREFIX + domain), domain);
        }
    }

    @Test
    void iam装配组织账号权限但不装配文件日历消息同步() {
        Set<String> domains = FoundationServerProfile.IAM.enabledDomains();
        assertTrue(domains.containsAll(Set.of("common", "app", "org", "account", "authz", "auth")));
        assertFalse(domains.contains("file"));
        assertFalse(domains.contains("calendar"));
        assertFalse(domains.contains("notify"));
        assertFalse(domains.contains("sync"));
    }

    @Test
    void common装配公共服务但不含组织与权限() {
        Set<String> domains = FoundationServerProfile.COMMON.enabledDomains();
        assertEquals(Set.of("common", "sequence", "audit"), domains);
        assertFalse(domains.contains("org"));
        assertFalse(domains.contains("authz"));
    }

    @Test
    void 显式域开关优先于profile默认值() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(FoundationServerProfileEnvironmentPostProcessor.PROFILE_PROPERTY,
                "minimal");
        environment.setProperty(PREFIX + "file", "true");
        PROCESSOR.postProcessEnvironment(environment, new SpringApplication(Object.class));
        assertEquals("true", environment.getProperty(PREFIX + "file"));
        assertEquals("false", environment.getProperty(PREFIX + "org"));
    }

    @Test
    void 非法profile快速失败() {
        assertThrows(IllegalArgumentException.class, () -> apply("everything"));
        assertThrows(IllegalArgumentException.class,
                () -> FoundationServerProfile.MINIMAL.enables("unknown"));
    }

    @Test
    void 条件装配与域开关联动() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(FileProbeConfiguration.class);
        runner.run(context -> assertTrue(context.containsBean("fileProbe")));
        runner.withPropertyValues(PREFIX + "file=false")
                .run(context -> assertFalse(context.containsBean("fileProbe")));
        runner.withPropertyValues(PREFIX + "file=true")
                .run(context -> assertTrue(context.containsBean("fileProbe")));
    }

    @Test
    void 全部业务装配类都声明了合法的域条件() throws Exception {
        List<String> classes = List.of(
                "com.foundation.common.config.CommonAutoConfiguration",
                "com.foundation.app.config.FoundationAppAutoConfiguration",
                "com.foundation.org.config.OrgAutoConfiguration",
                "com.foundation.account.config.AccountAutoConfiguration",
                "com.foundation.authz.config.AuthzAutoConfiguration",
                "com.foundation.auth.config.AuthAutoConfiguration",
                "com.foundation.notify.config.NotifyAutoConfiguration",
                "com.foundation.audit.config.AuditAutoConfiguration",
                "com.foundation.file.config.FileAutoConfiguration",
                "com.foundation.calendar.config.CalendarAutoConfiguration",
                "com.foundation.sequence.config.SequenceAutoConfiguration",
                "com.foundation.sync.config.SyncAutoConfiguration",
                "com.foundation.sync.scim.config.ScimAutoConfiguration",
                "com.foundation.sync.adapter.feishu.FeishuAutoConfiguration",
                "com.foundation.sync.adapter.ldap.LdapAutoConfiguration");
        Set<String> seen = new HashSet<>();
        for (String className : classes) {
            Class<?> type = Class.forName(className);
            ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
            assertTrue(condition != null, className + " 缺少域条件注解");
            assertEquals("foundation.server.domains", condition.prefix(), className);
            assertTrue(condition.matchIfMissing(), className + " 必须缺省启用");
            String domain = condition.name()[0];
            assertTrue(FoundationServerProfile.ALL_DOMAINS.contains(domain),
                    className + " 声明了未知域 " + domain);
            assertTrue(seen.add(domain), "域 " + domain + " 被多个装配类重复声明");
        }
        assertEquals(FoundationServerProfile.ALL_DOMAINS.size(), seen.size(),
                "所有业务域都应有对应装配类");
    }

    /** 与真实业务装配类同构的条件探针，避免测试依赖数据库与 Keycloak。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "foundation.server.domains", name = "file",
            havingValue = "true", matchIfMissing = true)
    static class FileProbeConfiguration {

        @Bean
        String fileProbe() {
            return "on";
        }
    }
}
