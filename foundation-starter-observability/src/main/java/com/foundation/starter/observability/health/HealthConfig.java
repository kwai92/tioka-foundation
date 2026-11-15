package com.foundation.starter.observability.health;

import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.observability.config.FoundationObservabilityProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 健康检查装配（F-02 BAS-24 §foundation-starter-observability）。
 *
 * <p>提供存活与就绪两个健康指示器，携带应用编码与服务名，便于探针与运维定位。
 * {@code /actuator/health/liveness|readiness} 探针组由应用侧
 * {@code management.endpoint.health.probes.enabled=true} 开启（与 BAS-21 部署一致）。
 *
 * <p>仅在类路径存在 actuator 时装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
public class HealthConfig {

    /** 存活探针（liveness）。 */
    @Bean
    @ConditionalOnMissingBean(name = "foundationLivenessIndicator")
    public HealthIndicator foundationLivenessIndicator(FoundationProperties foundationProperties,
                                                       FoundationObservabilityProperties properties) {
        return health(foundationProperties.getAppCode(), properties.getServiceName());
    }

    /** 就绪探针（readiness）。 */
    @Bean
    @ConditionalOnMissingBean(name = "foundationReadinessIndicator")
    public HealthIndicator foundationReadinessIndicator(FoundationProperties foundationProperties,
                                                       FoundationObservabilityProperties properties) {
        return health(foundationProperties.getAppCode(), properties.getServiceName());
    }

    private static HealthIndicator health(String appCode, String serviceName) {
        Health health = Health.up()
                .withDetail("app", appCode)
                .withDetail("service", serviceName)
                .build();
        return () -> health;
    }
}
