package com.foundation.keycloak;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Keycloak 管理接口可达性健康指示器（BAS-21 §3.10：就绪条件之一）。
 *
 * <p>调用既有 {@link KeycloakAdminClient#ping()} 判断连通性：成功 {@code up()}，
 * 失败或异常 {@code down(原因)}。异常消息只带失败原因，不携带密钥。
 */
public class KeycloakHealthIndicator implements HealthIndicator {

    private final KeycloakAdminClient adminClient;

    public KeycloakHealthIndicator(KeycloakAdminClient adminClient) {
        this.adminClient = adminClient;
    }

    @Override
    public Health health() {
        try {
            return adminClient.ping()
                    ? Health.up().build()
                    : Health.down().withDetail("reason", "Keycloak 管理接口不可达").build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("reason", e.getMessage()).build();
        }
    }
}
