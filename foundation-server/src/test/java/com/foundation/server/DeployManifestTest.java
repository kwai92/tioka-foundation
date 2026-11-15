package com.foundation.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * BAS-21「部署与交付」交付物守护（非集成测试）。
 *
 * <p>纯静态检查：读取 {@code deploy/} 与 {@code docs/ops/} 下的部署交付物，校验其与测试基座
 * （{@code foundation-test} 的 {@code FoundationContainers}）在中间件镜像版本、配置键名与账号约定上
 * 保持一致。不起容器、不联网、不执行 docker，目的是防止部署清单被无意改坏。
 */
class DeployManifestTest {

    /** 仓库根（含 {@code deploy/compose.yml} 的目录；从 user.dir 向上逐级查找）。 */
    private static final Path ROOT = locateRepoRoot();
    private static final Path DEPLOY = ROOT.resolve("deploy");
    private static final Path COMPOSE = DEPLOY.resolve("compose.yml");

    @Test
    void composeUsesSameMiddlewareVersionsAsTestBase() throws IOException {
        String compose = read(COMPOSE);
        assertThat(compose).contains("pgvector/pgvector:pg16");
        assertThat(compose).contains("redis:7-alpine");
        assertThat(compose).contains("apache/rocketmq:5.3.1");
        assertThat(compose).contains("quay.io/keycloak/keycloak:24.0");
    }

    @Test
    void composeHasMigrationAndAccountSeparation() throws IOException {
        String compose = read(COMPOSE);
        assertThat(compose).contains("foundation-migrate");
        assertThat(compose).contains("fdn_owner");
        assertThat(compose).contains("fdn_app");
        assertThat(compose).contains("service_completed_successfully");

        int serverIndex = compose.indexOf("foundation-server:");
        assertThat(serverIndex).as("compose 应包含 foundation-server 服务").isGreaterThanOrEqualTo(0);
        assertThat(compose.substring(serverIndex))
                .as("foundation-server 的健康检查应指向存活探针")
                .contains("/actuator/health/liveness");
    }

    @Test
    void composeCoversDeploySurfaceEnvVars() throws IOException {
        String compose = read(COMPOSE);
        List<String> required = List.of(
                "FOUNDATION_DB_URL",
                "FOUNDATION_DB_USERNAME",
                "FOUNDATION_DB_PASSWORD",
                "FOUNDATION_REDIS_HOST",
                "FOUNDATION_KEYCLOAK_URL",
                "FOUNDATION_KEYCLOAK_SECRET",
                "FOUNDATION_EVENT_TOPIC",
                "FOUNDATION_FLYWAY_ENABLED");
        for (String key : required) {
            assertThat(compose).as("compose 应覆盖环境变量 %s", key).contains(key);
        }
    }

    @Test
    void dockerfileMatchesTiokaImageForm() throws IOException {
        String dockerfile = read(DEPLOY.resolve("Dockerfile.foundation"));
        assertThat(dockerfile).contains("eclipse-temurin:17-jre");
        assertThat(dockerfile).contains("-Duser.timezone=UTC");
        assertThat(dockerfile).contains("OTEL_SERVICE_NAME=foundation");
    }

    @Test
    void helmChartContainsRequiredFiles() {
        assertThat(DEPLOY.resolve("helm/foundation/Chart.yaml")).isRegularFile();
        assertThat(DEPLOY.resolve("helm/foundation/values.yaml")).isRegularFile();
        assertThat(DEPLOY.resolve("helm/foundation/values-prod.yaml")).isRegularFile();
        List<String> templates = List.of(
                "foundation-deployment",
                "foundation-ui-deployment",
                "migration-job",
                "keycloak-deployment",
                "ingress",
                "configmap",
                "secret",
                "hpa");
        for (String template : templates) {
            assertThat(DEPLOY.resolve("helm/foundation/templates/" + template + ".yaml"))
                    .as("Helm 模板应存在：%s.yaml", template)
                    .isRegularFile();
        }
    }

    @Test
    void initSqlCreatesDatabasesAndAccounts() throws IOException {
        String sql = read(DEPLOY.resolve("init/foundation-db.sql"));
        assertThat(sql).contains("CREATE DATABASE foundation");
        assertThat(sql).contains("CREATE DATABASE keycloak");
        assertThat(sql).contains("fdn_owner");
        assertThat(sql).contains("fdn_app");
    }

    @Test
    void realmJsonIsValidAndUsesEnterpriseRealm() throws IOException {
        String raw = read(DEPLOY.resolve("init/foundation-realm.json"));
        JsonNode node = new ObjectMapper().readTree(raw);
        assertThat(node.path("realm").asText()).isEqualTo("enterprise");
    }

    @Test
    void deploymentArtifactsContainNoForbiddenColumnLiterals() throws IOException {
        // 硬约束（ADR-029 第 60 条）：单企业独立部署，不得出现租户列 / 租户表字面量；
        // 断言目标字符串用拼接构造，使本测试自身也不含被禁字面量。
        String forbiddenColumn = "tenant" + "_id";
        String forbiddenTable = "sys" + "_tenant";

        List<Path> files = new ArrayList<>();
        files.addAll(listTextFiles(DEPLOY));
        files.addAll(listTextFiles(ROOT.resolve("docs/ops")));
        assertThat(files).as("deploy/ 与 docs/ops/ 下应有交付物文本文件").isNotEmpty();

        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(text).as("文件不得包含禁用字面量：%s", file)
                    .doesNotContain(forbiddenColumn)
                    .doesNotContain(forbiddenTable);
        }
    }

    @Test
    void probesAndOfflineManifestPresent() throws IOException {
        String yaml = read(ROOT.resolve("foundation-server/src/main/resources/application.yml"));
        assertThat(yaml).contains("probes:");
        assertThat(yaml).contains("enabled: true");
        assertThat(read(DEPLOY.resolve("offline/manifest")).trim()).isNotEmpty();
    }

    private static String read(Path path) throws IOException {
        assertThat(path).as("文件应存在：%s", path).isRegularFile();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<Path> listTextFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    private static Path locateRepoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("deploy/compose.yml"))) {
                return dir;
            }
        }
        return fail("未找到含 deploy/compose.yml 的仓库根（自 " + start + " 向上逐级查找）");
    }
}
