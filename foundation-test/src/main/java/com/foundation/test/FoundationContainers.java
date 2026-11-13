package com.foundation.test;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * JVM 内单例容器基座（F-02 BAS-01 §闸门基座）：PostgreSQL 16（pgvector）、Redis 7、
 * Keycloak 24（导入 {@code keycloak/foundation-realm.json}）。
 *
 * <p>RocketMQ 懒启动，仅 {@code @Tag("mq")} / 对应闸门用例拉起，避免默认集成测试等镜像与内存。
 */
public final class FoundationContainers {

    /** 闸门容器：PostgreSQL 16（pgvector）；Testcontainers URL 会覆盖 application.yml。 */
    public static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("foundation")
                    .withUsername("foundation")
                    .withPassword("foundation")
                    .withCommand("postgres", "-c", "max_connections=200");

    /** 与 deploy/compose.yml 同版本：Redis 7。 */
    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                            .withStartupTimeout(Duration.ofMinutes(2)));

    /** 与 deploy/compose.yml 同版本；调用 {@link RocketMqCluster#ensureStarted()} 才启动。 */
    public static final RocketMqCluster ROCKETMQ = new RocketMqCluster();

    /** 测试服务账号（见 {@code keycloak/foundation-realm.json}，仅测试环境）。 */
    public static final String KEYCLOAK_CLIENT_ID = "foundation-admin";
    public static final String KEYCLOAK_CLIENT_SECRET = "foundation-test-admin-secret";

    /**
     * 测试用取令牌 realm：闸门容器把服务账号建在应用 realm {@code enterprise} 内；
     * 生产默认 {@code master}（{@code foundation.keycloak.admin-realm}）。
     */
    public static final String KEYCLOAK_ADMIN_REALM = "enterprise";

    private static final Object KEYCLOAK_LOCK = new Object();
    private static KeycloakContainer keycloak;

    static {
        Startables.deepStart(PG, REDIS).join();
    }

    private FoundationContainers() {
    }

    /** Keycloak 单例：首次调用才启动，容器内导入 {@code foundation-realm.json}。 */
    public static KeycloakContainer keycloak() {
        synchronized (KEYCLOAK_LOCK) {
            if (keycloak == null) {
                KeycloakContainer container = new KeycloakContainer("quay.io/keycloak/keycloak:24.0")
                        .withRealmImportFile("keycloak/foundation-realm.json");
                container.start();
                keycloak = container;
            }
            return keycloak;
        }
    }

    /** 注册 PostgreSQL 与 Redis 连接属性（供 {@code @DynamicPropertySource} 使用）。 */
    public static void register(DynamicPropertyRegistry registry) {
        // application.yml 的 spring.datasource.* 引用 foundation.db.*，此处只需覆盖前者
        registry.add("foundation.db.url", () -> appendUtcSession(PG.getJdbcUrl()));
        registry.add("foundation.db.username", PG::getUsername);
        registry.add("foundation.db.password", PG::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /** 追加 Keycloak 管理接口属性（供 {@code @DynamicPropertySource} 使用）。 */
    public static void registerKeycloak(DynamicPropertyRegistry registry) {
        KeycloakContainer container = keycloak();
        registry.add("foundation.keycloak.server-url", () -> container.getAuthServerUrl().replaceAll("/$", ""));
        registry.add("foundation.keycloak.admin-realm", () -> KEYCLOAK_ADMIN_REALM);
        registry.add("foundation.keycloak.client-id", () -> KEYCLOAK_CLIENT_ID);
        registry.add("foundation.keycloak.client-secret", () -> KEYCLOAK_CLIENT_SECRET);
    }

    /** 会话时区统一 UTC（00-施工总纲 §2.2）。 */
    public static String appendUtcSession(String jdbcUrl) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "options=-c%20TimeZone%3DUTC";
    }

    /**
     * RocketMQ 5.3.1 NameServer + Broker（共享 Docker network）。
     * 懒启动：不占用默认集成测试的内存与启动时间。
     */
    public static final class RocketMqCluster {

        private static final DockerImageName ROCKETMQ_IMAGE = DockerImageName.parse("apache/rocketmq:5.3.1");

        private final Object lock = new Object();
        private volatile boolean started;
        private GenericContainer<?> nameServer;

        public void ensureStarted() {
            if (started) {
                return;
            }
            synchronized (lock) {
                if (started) {
                    return;
                }
                Network network = Network.newNetwork();
                nameServer = new GenericContainer<>(ROCKETMQ_IMAGE)
                        .withNetwork(network)
                        .withNetworkAliases("rmqnamesrv")
                        .withExposedPorts(9876)
                        .withCommand("sh", "mqnamesrv")
                        .waitingFor(Wait.forLogMessage(".*The Name Server boot success.*", 1)
                                .withStartupTimeout(Duration.ofMinutes(2)));
                nameServer.start();

                GenericContainer<?> broker = new GenericContainer<>(ROCKETMQ_IMAGE)
                        .withNetwork(network)
                        .withNetworkAliases("broker-fdn")
                        .withExposedPorts(10909, 10911, 10912)
                        .dependsOn(nameServer)
                        .withEnv("NAMESRV_ADDR", "rmqnamesrv:9876")
                        .withCommand("sh", "-c",
                                // 镜像 WORKDIR 为 .../bin，配置在 ../conf
                                "echo \"brokerIP1=$(hostname -i)\" >> ../conf/broker.conf && "
                                        + "echo \"autoCreateTopicEnable=true\" >> ../conf/broker.conf && "
                                        + "sh mqbroker -n rmqnamesrv:9876 -c ../conf/broker.conf")
                        .waitingFor(Wait.forLogMessage(".*The broker.*boot success.*", 1)
                                .withStartupTimeout(Duration.ofMinutes(3)));
                broker.start();
                started = true;
            }
        }

        public String nameServerAddr() {
            ensureStarted();
            return nameServer.getHost() + ":" + nameServer.getMappedPort(9876);
        }

        public int nameServerPort() {
            ensureStarted();
            return nameServer.getMappedPort(9876);
        }

        public boolean isStarted() {
            return started;
        }
    }
}
