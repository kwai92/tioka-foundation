package com.foundation.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;

/**
 * BAS-23 验收用例：示例工程只以 foundation-parent 为父工程、依赖不写版本，
 * 版本管理链（foundation-parent → foundation-dependencies → Spring Boot BOM）仍能解析。
 */
class ExampleBuildTest {

    @Test
    void ulidCreatorVersionResolvedFromDependencyManagement() {
        String id = ExampleApp.newId("dt");
        assertThat(id).startsWith("dt_");
        // ULID 为 26 字符 Crockford Base32
        assertThat(id).hasSize(29);
    }

    @Test
    void springBootVersionResolvedFromBom() {
        assertThat(SpringBootVersion.getVersion()).startsWith("3.2.");
    }
}
