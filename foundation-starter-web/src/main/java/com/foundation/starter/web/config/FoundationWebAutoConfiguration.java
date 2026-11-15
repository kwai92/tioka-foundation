package com.foundation.starter.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.config.CoreAutoConfiguration;
import com.foundation.starter.web.error.GlobalExceptionHandler;
import com.foundation.starter.web.filter.IdempotencyFilter;
import com.foundation.starter.web.filter.IdempotencyStore;
import com.foundation.starter.web.filter.InMemoryIdempotencyStore;
import com.foundation.starter.web.filter.RequestLogFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * Web 层自动装配（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>装配全局异常处理、幂等、请求日志与 OpenAPI；仅在 Servlet Web 应用且 {@code foundation.enabled}
 * 时生效，各 Bean 均可被业务侧同名 Bean 覆盖。
 */
@AutoConfiguration(after = CoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "foundation", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(FoundationWebProperties.class)
@Import(OpenApiConfig.class)
public class FoundationWebAutoConfiguration {

    /** 全局异常处理器。 */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** 幂等键存储，缺省进程内实现。 */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStore idempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    /** 幂等过滤器。 */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyFilter idempotencyFilter(IdempotencyStore store,
                                               FoundationWebProperties properties,
                                               ObjectMapper objectMapper) {
        return new IdempotencyFilter(store, properties, objectMapper);
    }

    /** 幂等过滤器注册。 */
    @Bean
    @ConditionalOnMissingBean(name = "idempotencyFilterRegistration")
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter filter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setUrlPatterns(List.of("/*"));
        return registration;
    }

    /** 请求访问日志过滤器。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "foundation.web", name = "request-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public RequestLogFilter requestLogFilter(FoundationWebProperties properties) {
        return new RequestLogFilter(properties);
    }

    /** 请求访问日志过滤器注册。 */
    @Bean
    @ConditionalOnMissingBean(name = "requestLogFilterRegistration")
    @ConditionalOnProperty(prefix = "foundation.web", name = "request-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RequestLogFilter> requestLogFilterRegistration(RequestLogFilter filter) {
        FilterRegistrationBean<RequestLogFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setUrlPatterns(List.of("/*"));
        return registration;
    }
}
