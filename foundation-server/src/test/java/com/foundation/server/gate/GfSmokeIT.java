package com.foundation.server.gate;

import com.foundation.keycloak.KeycloakAdminClient;
import com.foundation.test.FoundationContainers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-02 BAS-01 闸门（F-00 §6.1）：Foundation 服务、Keycloak、PostgreSQL、Redis 基座起得来，管理接口可连。
 *
 * <p>运行：{@code mvn -B verify -Pgate}。
 */
@Tag("gate")
@SpringBootTest
class GfSmokeIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
        FoundationContainers.registerKeycloak(registry);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private KeycloakAdminClient keycloakAdminClient;

    @Test
    void foundationServiceStartsWithBaselineSchemaOnPostgres() {
        assertThat(context.containsBean("foundationApplication")).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.fdn_outbox')", String.class))
                .isEqualTo("fdn_outbox");
    }

    @Test
    void redisIsReachable() {
        redis.opsForValue().set("fdn:gate:smoke", "ok");
        assertThat(redis.opsForValue().get("fdn:gate:smoke")).isEqualTo("ok");
        redis.delete("fdn:gate:smoke");
    }

    @Test
    void keycloakAdminApiIsReachable() {
        assertThat(keycloakAdminClient.ping()).isTrue();
        assertThat(keycloakAdminClient.accessToken()).isNotBlank();
    }
}
