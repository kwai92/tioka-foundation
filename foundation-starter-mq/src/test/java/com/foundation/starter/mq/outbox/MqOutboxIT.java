package com.foundation.starter.mq.outbox;

import com.foundation.starter.mq.config.FoundationMqProperties;
import com.foundation.starter.mq.consumer.ConsumedStore;
import com.foundation.starter.mq.event.DomainEvent;
import com.foundation.starter.mq.mq.MqTemplate;
import com.foundation.starter.mq.relay.OutboxRelay;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * outbox / 消费幂等 PostgreSQL 集成测试（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>surefire 默认排除 {@code *IT}；本用例由 failsafe 在具备 Docker 时执行。
 * 直接用 {@link PostgreSQLContainer}（不借道 {@code FoundationContainers}，避免其静态块无条件启动容器）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MqOutboxIT.ItApp.class)
class MqOutboxIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("foundation")
            .withUsername("foundation")
            .withPassword("foundation");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired
    Outbox outbox;

    @Autowired
    OutboxRelay relay;

    @Autowired
    ConsumedStore consumedStore;

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS fdn_outbox (
                        id          VARCHAR(32)  PRIMARY KEY,
                        topic       VARCHAR(128) NOT NULL,
                        event_type  VARCHAR(128) NOT NULL,
                        aggregate   VARCHAR(64)  NOT NULL,
                        payload     JSONB        NOT NULL,
                        status      VARCHAR(16)  NOT NULL DEFAULT 'NEW',
                        retry_count INT          NOT NULL DEFAULT 0,
                        create_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
                        sent_at     TIMESTAMPTZ
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS fdn_consumed (
                        consumer    VARCHAR(64)  NOT NULL,
                        message_id  VARCHAR(128) NOT NULL,
                        consumed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        PRIMARY KEY (consumer, message_id)
                    )
                    """);
            statement.execute("DELETE FROM fdn_outbox");
            statement.execute("DELETE FROM fdn_consumed");
        }
    }

    @Test
    void 追加后可拉取且载荷为JSON文本() {
        outbox.append(DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_1", Map.of("id", "dt_1")));

        List<OutboxEntry> pending = outbox.fetchPending(100);

        assertTrue(pending.stream()
                .anyMatch(entry -> "dt_1".equals(entry.aggregate())
                        && entry.payload().contains("dt_1")
                        && entry.id().startsWith("evt_")));
    }

    @Test
    void 中继投递后状态变已发送() {
        outbox.append(DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_2", Map.of("id", "dt_2")));

        assertTrue(relay.pollOnce() >= 1);
        assertTrue(outbox.fetchPending(100).stream().noneMatch(entry -> "dt_2".equals(entry.aggregate())));
    }

    @Test
    void 消费幂等第二次为假() {
        assertTrue(consumedStore.markConsumed("it-consumer", "m-it-1"));
        assertFalse(consumedStore.markConsumed("it-consumer", "m-it-1"));
    }

    /**
     * 轻装配应用：MapperScan + 记录型 MqTemplate 与中继。
     *
     * <p>{@code annotationClass = Mapper.class} 是必需的，**不要**改回只写 basePackages：
     * MyBatis 的 {@code ClassPathMapperScanner} 默认把包下**所有接口**都注册成 Mapper，
     * 而 {@code com.foundation.starter.mq} 下还有 {@code MqTemplate} / {@code Outbox} /
     * {@code ConsumedStore} / {@code EventSink} 等非 Mapper 接口。其中 {@code mqTemplate}
     * 会与下方 {@code @Bean MqTemplate mqTemplate()} 撞名，抛
     * {@code ConflictingBeanDefinitionException} 使 ApplicationContext 加载失败，
     * 连带 CI 的 verify 步骤失败、GF 闸门与兼容矩阵两个 job 被 skip。
     * 限定注解后只扫描真正标注 {@code @Mapper} 的 {@code OutboxMapper} 与 {@code ConsumedMessageMapper}。
     */
    @SpringBootApplication
    @MapperScan(basePackages = "com.foundation.starter.mq", annotationClass = Mapper.class)
    static class ItApp {

        @Bean
        MqTemplate mqTemplate() {
            return new RecordingMqTemplate();
        }

        @Bean
        OutboxRelay outboxRelay(Outbox outbox, MqTemplate mqTemplate, FoundationMqProperties properties) {
            return new OutboxRelay(outbox, mqTemplate, properties);
        }
    }

    /** 测试替身：记录型 MqTemplate。 */
    static final class RecordingMqTemplate implements MqTemplate {

        private final List<String> destinations = new ArrayList<>();

        @Override
        public void send(String topic, String tag, String body) {
            destinations.add(topic + ":" + tag);
        }
    }
}
