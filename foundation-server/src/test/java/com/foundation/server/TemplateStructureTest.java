package com.foundation.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.foundation.authz.domain.ManifestDocument;
import com.foundation.authz.domain.ManifestMenu;
import com.foundation.authz.domain.ManifestParser;
import com.foundation.authz.domain.ManifestPermission;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BAS-25「工程模板与 CI 模板」结构与格式守卫（非集成测试）。
 *
 * <p>纯静态检查：读取 {@code foundation-template/} 与 {@code docs/权限清单.json}，用真实解析器
 * {@link ManifestParser} 校验清单格式。不起容器、不联网、不执行 maven；目的是防止模板结构或
 * 权限清单格式被无意改坏（模板是复制起点，损坏会扩散到所有新子系统）。
 */
class TemplateStructureTest {

    /** 模板根目录（从 user.dir 逐级向上查找名为 foundation-template 的目录）。 */
    private static final Path TEMPLATE = locateTemplate();

    @Test
    void templateContainsRequiredFiles() {
        assertThat(TEMPLATE.resolve("README.md")).isRegularFile();
        assertThat(TEMPLATE.resolve("pom.xml")).isRegularFile();
        assertThat(TEMPLATE.resolve(".github/workflows/ci.yml")).isRegularFile();
        assertThat(TEMPLATE.resolve("Dockerfile")).isRegularFile();
        assertThat(TEMPLATE.resolve("docs/权限清单.json")).isRegularFile();
        assertThat(TEMPLATE.resolve("scripts/rename.sh")).isRegularFile();
        assertThat(TEMPLATE.resolve("scripts/rename.ps1")).isRegularFile();
        assertThat(TEMPLATE.resolve("scripts/self-check.sh")).isRegularFile();
        assertThat(TEMPLATE.resolve("scripts/check-permission-manifest.sh")).isRegularFile();
        assertThat(TEMPLATE.resolve("deploy/helm/{app}/Chart.yaml")).isRegularFile();

        assertThat(TEMPLATE.resolve("{app}-server")).isDirectory();
        assertThat(TEMPLATE.resolve("{app}-example-api")).isDirectory();
        assertThat(TEMPLATE.resolve("{app}-example-biz")).isDirectory();
        assertThat(TEMPLATE.resolve("{app}-ui")).isDirectory();
    }

    @Test
    void permissionManifestParsesWithFoundationParser() throws IOException {
        String raw = Files.readString(TEMPLATE.resolve("docs/权限清单.json"), StandardCharsets.UTF_8);
        // 模板刻意保留字面占位符；替换为合法编码后再交给真实解析器校验格式。
        byte[] manifest = raw.replace("{app}", "sample").getBytes(StandardCharsets.UTF_8);

        ManifestDocument document = new ManifestParser().parse(manifest);

        List<ManifestMenu> menus = document.menus();
        List<ManifestPermission> permissions = document.permissions();
        assertThat(menus).isNotEmpty();
        assertThat(permissions).isNotEmpty();

        Set<String> permissionCodes = new HashSet<>();
        for (ManifestPermission permission : permissions) {
            permissionCodes.add(permission.code());
        }
        for (ManifestMenu menu : menus) {
            String permissionCode = menu.permissionCode();
            if (permissionCode != null && !permissionCode.isBlank()) {
                assertThat(permissionCodes)
                        .as("菜单 %s 的 permissionCode 必须在 permissions 中声明", menu.code())
                        .contains(permissionCode);
            }
        }
    }

    @Test
    void exampleAutoConfigurationImportsMatchesFqcn() throws IOException {
        Path imports = TEMPLATE.resolve("{app}-example-biz/src/main/resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertThat(imports).isRegularFile();
        assertThat(Files.readString(imports, StandardCharsets.UTF_8).trim())
                .isEqualTo("com.foundation.{app}.example.config.ExampleAutoConfiguration");
    }

    @Test
    void ciTemplateCoversAllTasks() throws IOException {
        String ci = Files.readString(TEMPLATE.resolve(".github/workflows/ci.yml"), StandardCharsets.UTF_8);
        assertThat(ci).contains("verify");
        assertThat(ci).contains("Arch");
        assertThat(ci).contains("dependency-check");
        assertThat(ci).contains("权限清单");
        assertThat(ci).contains("check-permission-manifest");
        assertThat(ci).contains("docker build");
    }

    private static Path locateTemplate() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("foundation-template");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return fail("未找到 foundation-template 目录（自 " + start + " 向上逐级查找）");
    }
}
