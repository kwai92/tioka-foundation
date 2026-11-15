package com.foundation.server.gate;

import com.foundation.server.profile.FoundationServerProfile;
import com.foundation.server.profile.FoundationServerProfileEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GA 闸门 {@code GaModularityGateIT} 中<b>不需要 Docker</b>的部分（36 号规划 §7）。
 *
 * <p>原有 {@code FoundationServerProfileTest} 已验证 profile 映射与「15 个真实业务装配类
 * 各自声明合法且不重复的域条件」；本类补上两件此前没有的守门：
 * <ol>
 *   <li>把四种 profile 的能力域<b>冻结成入库基线</b>，擅自扩大/缩小装配范围即 CI 变红；</li>
 *   <li>用 15 个与真实装配类同构的条件探针，在纯 Spring 上下文里跑通
 *       「profile → EPP 属性 → {@code @ConditionalOnProperty} → Bean 装配」整条链路，
 *       证明关闭单域不会留下半装配状态。</li>
 * </ol>
 *
 * <p><b>刻意不做的部分</b>：四种 profile 各自完整启动 {@code foundation-server} 并断言
 * 只暴露声明的 REST 能力，需要 PG / Redis / Keycloak，属 Testcontainers 端到端 IT，
 * 仍由 CI 判定（本机无 Docker）。本类不冒充该 IT。
 *
 * <p>基线刷新：{@code mvn -pl foundation-server test -Dtest=GaModularityGateTest
 * -Dfoundation.contract.update=true}。
 */
class GaModularityGateTest {

    private static final Path BASELINE =
            Path.of("src/test/resources/contracts/server-profile-domains-v1.txt");

    private static final String UPDATE_FLAG = "foundation.contract.update";

    private static final String PREFIX =
            FoundationServerProfileEnvironmentPostProcessor.DOMAIN_PREFIX;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DomainProbeConfiguration.class);

    @Test
    void 四种profile的能力域与冻结基线一致() throws Exception {
        String current = baseline();
        if (Boolean.getBoolean(UPDATE_FLAG)) {
            Files.createDirectories(BASELINE.getParent());
            Files.writeString(BASELINE, current, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(BASELINE),
                "装配 profile 基线缺失，请先以 -Dfoundation.contract.update=true 生成：" + BASELINE);
        assertEquals(Files.readString(BASELINE, StandardCharsets.UTF_8), current,
                "四种 profile 声明的能力域发生变化。扩大装配范围会引入未经验证的依赖，"
                        + "缩小会破坏既有接入方；变更需评审后刷新基线并在发版说明登记（36 号规划 §6）。");
    }

    @Test
    void 探针装配集合与profile声明逐一对齐() {
        for (FoundationServerProfile profile : FoundationServerProfile.values()) {
            Set<String> expected = beanNames(profile.enabledDomains());
            runner.withPropertyValues(propertiesFor(profile))
                    .run(context -> {
                        assertEquals(expected, probeBeans(context), profile.key());
                        assertEquals(expected.size(), probeBeans(context).size(), profile.key());
                    });
        }
    }

    @Test
    void EPP注入的属性与条件装配联动() {
        for (FoundationServerProfile profile : FoundationServerProfile.values()) {
            MockEnvironment environment = new MockEnvironment();
            environment.setProperty(
                    FoundationServerProfileEnvironmentPostProcessor.PROFILE_PROPERTY,
                    profile.key());
            new FoundationServerProfileEnvironmentPostProcessor()
                    .postProcessEnvironment(environment, new SpringApplication(Object.class));
            String[] injected = FoundationServerProfile.ALL_DOMAINS.stream()
                    .map(domain -> PREFIX + domain + "=" + environment.getProperty(PREFIX + domain))
                    .toArray(String[]::new);
            runner.withPropertyValues(injected)
                    .run(context -> assertEquals(beanNames(profile.enabledDomains()),
                            probeBeans(context), profile.key()));
        }
    }

    @Test
    void 关闭单域只下线该域_不影响其他域() {
        runner.withPropertyValues(propertiesFor(FoundationServerProfile.FULL))
                .withPropertyValues(PREFIX + "file=false")
                .run(context -> {
                    Set<String> beans = probeBeans(context);
                    assertFalse(beans.contains("probeFile"));
                    assertTrue(beans.contains("probeOrg"));
                    assertTrue(beans.contains("probeCommon"));
                    assertEquals(FoundationServerProfile.ALL_DOMAINS.size() - 1, beans.size());
                });
    }

    private static String baseline() {
        StringBuilder text = new StringBuilder();
        for (FoundationServerProfile profile : FoundationServerProfile.values()) {
            text.append(profile.key()).append('=')
                    .append(String.join(",", profile.enabledDomains())).append('\n');
        }
        return text.toString();
    }

    private static String[] propertiesFor(FoundationServerProfile profile) {
        return FoundationServerProfile.ALL_DOMAINS.stream()
                .map(domain -> PREFIX + domain + "=" + profile.enables(domain))
                .toArray(String[]::new);
    }

    private static Set<String> beanNames(Set<String> domains) {
        Set<String> names = new LinkedHashSet<>();
        for (String domain : domains) {
            names.add("probe" + domain.substring(0, 1).toUpperCase(Locale.ROOT) + domain.substring(1));
        }
        return names;
    }

    private static Set<String> probeBeans(org.springframework.context.ConfigurableApplicationContext context) {
        Set<String> names = new LinkedHashSet<>(context.getBeansOfType(String.class).keySet());
        names.removeIf(name -> !name.startsWith("probe"));
        return names;
    }

    /**
     * 与 15 个真实业务 {@code AutoConfiguration} 同构的条件探针：域名、前缀、缺省启用
     * 完全一致，但不创建需要数据库 / Keycloak 的业务 Bean，因此本机可跑。
     */
    @Configuration(proxyBeanMethods = false)
    static class DomainProbeConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "common",
                havingValue = "true", matchIfMissing = true)
        String probeCommon() {
            return "common";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "app",
                havingValue = "true", matchIfMissing = true)
        String probeApp() {
            return "app";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "org",
                havingValue = "true", matchIfMissing = true)
        String probeOrg() {
            return "org";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "account",
                havingValue = "true", matchIfMissing = true)
        String probeAccount() {
            return "account";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "authz",
                havingValue = "true", matchIfMissing = true)
        String probeAuthz() {
            return "authz";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "auth",
                havingValue = "true", matchIfMissing = true)
        String probeAuth() {
            return "auth";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "notify",
                havingValue = "true", matchIfMissing = true)
        String probeNotify() {
            return "notify";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "audit",
                havingValue = "true", matchIfMissing = true)
        String probeAudit() {
            return "audit";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "file",
                havingValue = "true", matchIfMissing = true)
        String probeFile() {
            return "file";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "calendar",
                havingValue = "true", matchIfMissing = true)
        String probeCalendar() {
            return "calendar";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "sequence",
                havingValue = "true", matchIfMissing = true)
        String probeSequence() {
            return "sequence";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "sync",
                havingValue = "true", matchIfMissing = true)
        String probeSync() {
            return "sync";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "scim",
                havingValue = "true", matchIfMissing = true)
        String probeScim() {
            return "scim";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "feishu",
                havingValue = "true", matchIfMissing = true)
        String probeFeishu() {
            return "feishu";
        }

        @Bean
        @ConditionalOnProperty(prefix = "foundation.server.domains", name = "ldap",
                havingValue = "true", matchIfMissing = true)
        String probeLdap() {
            return "ldap";
        }
    }
}
