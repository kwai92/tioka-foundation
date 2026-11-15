package com.foundation.server.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.server.profile.FoundationServerProfile;
import com.foundation.starter.core.config.CoreAutoConfiguration;
import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADP-11：兼容矩阵 CI 的**组合探针**。
 *
 * <p>CI 用 strategy.matrix 把 3 种运行模式 × 2 档装配 profile 注入
 * {@code FOUNDATION_COMPAT_MODE} / {@code FOUNDATION_COMPAT_PROFILE}；本类真正消费这两个值，
 * 因此矩阵不是「同一套测试空跑 6 次」。本机未注入时按默认组合 {@code foundation/full} 运行，
 * 与既有全量单测完全一致。
 *
 * <p>刻意不做的部分：四种 profile 的完整服务启动需 PG / Redis / Keycloak，属 Testcontainers IT；
 * 独立模式下 HTTP 客户端不装配由 {@code RuntimeModeAssemblyTest} 断言，此处不重复。
 */
class CompatibilityMatrixProbeTest {

    private static final Path MATRIX =
            Path.of("../docs/contracts/compatibility-matrix-v1.json");
    private static final Path PROFILE_BASELINE =
            Path.of("src/test/resources/contracts/server-profile-domains-v1.txt");

    private static String comboValue(String env, String property, String fallback) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) {
            value = System.getProperty(property);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String mode() {
        return comboValue("FOUNDATION_COMPAT_MODE", "foundation.client.mode", "foundation");
    }

    private static String profile() {
        return comboValue("FOUNDATION_COMPAT_PROFILE", "foundation.server.profile", "full");
    }

    private static JsonNode matrix() throws Exception {
        assertTrue(Files.exists(MATRIX), "兼容矩阵契约缺失：" + MATRIX);
        return new ObjectMapper().readTree(Files.readString(MATRIX, StandardCharsets.UTF_8));
    }

    @Test
    void 当前组合在矩阵契约声明的支持范围内() throws Exception {
        String combo = mode() + "/" + profile();
        boolean declared = false;
        for (JsonNode node : matrix().path("ciMatrix").path("combinations")) {
            if (combo.equals(node.asText())) {
                declared = true;
                break;
            }
        }
        assertTrue(declared, "CI 正在跑的组合 " + combo
                + " 未登记在 compatibility-matrix-v1.json，请先评审兼容边界再补登记");
    }

    @Test
    void 契约里的模式与profile都能被代码解析() throws Exception {
        JsonNode contract = matrix();
        for (JsonNode node : contract.path("runtimeModes")) {
            assertNotNull(FoundationRuntimeMode.parse(node.asText()), node.asText());
        }
        for (JsonNode node : contract.path("serverProfiles")) {
            assertNotNull(FoundationServerProfile.from(node.asText()).enabledDomains(), node.asText());
        }
        assertEquals(FoundationRuntimeMode.values().length, contract.path("runtimeModes").size());
        assertEquals(FoundationServerProfile.values().length, contract.path("serverProfiles").size());
    }

    @Test
    void 按当前组合装配出正确的运行模式Bean() {
        FoundationRuntimeMode expected = FoundationRuntimeMode.parse(mode());
        new ApplicationContextRunner()
                .withUserConfiguration(CoreAutoConfiguration.class)
                .withPropertyValues("foundation.client.mode=" + mode())
                .run(context -> {
                    assertNull(context.getStartupFailure(), "上下文启动失败：" + mode());
                    assertEquals(1, context.getBeansOfType(FoundationRuntimeMode.class).size());
                    assertEquals(expected, context.getBean(FoundationRuntimeMode.class),
                            "CI 注入的运行模式没有被 core 装配消费");
                });
    }

    @Test
    void 当前profile的能力域与冻结基线一致() throws Exception {
        List<String> lines = Files.readAllLines(PROFILE_BASELINE, StandardCharsets.UTF_8);
        String expectedLine = lines.stream()
                .filter(line -> line.startsWith(profile() + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("基线缺少 profile：" + profile()));
        assertEquals(expectedLine,
                profile() + "=" + String.join(",",
                        FoundationServerProfile.from(profile()).enabledDomains()),
                "profile 能力域与冻结基线不一致，改动需评审后刷新基线");
    }

    @Test
    void 总开关优先于能力开关_CM02() {
        new ApplicationContextRunner()
                .withUserConfiguration(CoreAutoConfiguration.class)
                .withPropertyValues("foundation.enabled=false",
                        "foundation.core.enabled=true",
                        "foundation.client.mode=foundation")
                .run(context -> assertEquals(0,
                        context.getBeansOfType(FoundationRuntimeMode.class).size(),
                        "foundation.enabled=false 时 core 不应装配任何 Bean"));
    }
}
