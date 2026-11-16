package com.foundation.starter.observability.config;

import com.foundation.starter.core.config.CoreAutoConfiguration;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.observability.health.HealthConfig;
import com.foundation.starter.observability.otel.OtelConfig;
import com.foundation.starter.observability.trace.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * 可观测接入自动装配（F-02 BAS-24 §foundation-starter-observability）。
 *
 * <p>代理 OpenTelemetry 与健康检查装配，并在 Servlet web 应用中注册 traceId 过滤器。
 * 本类自身不出现 servlet / web 类型，非 web 进程不会因类加载失败而中断。
 */
@AutoConfiguration(after = CoreAutoConfiguration.class)
@ConditionalOnFoundationCapability("observability")
@EnableConfigurationProperties(FoundationObservabilityProperties.class)
@Import({OtelConfig.class, HealthConfig.class})
public class FoundationObservabilityAutoConfiguration {

    /** Servlet 场景的 traceId 透传：过滤器紧贴最前执行，确保后续组件都能读到上下文。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    static class ServletTraceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public TraceIdFilter foundationTraceIdFilter(FoundationProperties foundationProperties) {
            return new TraceIdFilter(foundationProperties);
        }

        @Bean
        @ConditionalOnMissingBean
        public FilterRegistrationBean<TraceIdFilter> foundationTraceIdFilterRegistration(TraceIdFilter filter) {
            FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
            registration.setUrlPatterns(List.of("/*"));
            return registration;
        }
    }
}
