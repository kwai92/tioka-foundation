package com.foundation.starter.observability.otel;

import com.foundation.starter.observability.config.FoundationObservabilityProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 装配（F-02 BAS-24 §foundation-starter-observability）。
 *
 * <p>启动器只做约定与端点装配：暴露全局 {@link OpenTelemetry} 与按服务名取到的 {@link Tracer}，
 * 供业务注入打点。采样率 {@code foundation.observability.trace-sample-rate} 由应用侧 SDK/agent
 * 生效（starter 不伪造未实现的能力），本类只保证 Tracer 的来源与命名一致。
 *
 * <p>仅在类路径存在 OpenTelemetry API 时装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.opentelemetry.api.OpenTelemetry")
public class OtelConfig {

    /** 全局 OpenTelemetry 实例（由应用侧 agent 或 SDK 初始化）。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    /** 按服务名创建的 Tracer（业务注入后用其创建 span）。 */
    @Bean
    @ConditionalOnMissingBean
    public Tracer tracer(OpenTelemetry openTelemetry, FoundationObservabilityProperties properties) {
        return openTelemetry.getTracer(properties.getServiceName());
    }
}
