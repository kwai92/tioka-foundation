package com.foundation.starter.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.http.BearerTokenSupplier;
import com.foundation.starter.security.datascope.DataScopeFilterProvider;
import com.foundation.starter.security.datascope.DefaultDataScopeFilterProvider;
import com.foundation.starter.security.permission.DeniedAuditSink;
import com.foundation.starter.security.permission.PermissionAspect;
import com.foundation.starter.security.principal.CurrentUserArgumentResolver;
import com.foundation.starter.security.principal.JwtPrincipalResolver;
import com.foundation.starter.security.principal.OpaqueTokenPrincipalResolver;
import com.foundation.starter.security.principal.PrincipalAuthenticationFilter;
import com.foundation.starter.security.principal.PrincipalResolver;
import com.foundation.starter.security.principal.RequestHeaderTokenExtractor;
import com.foundation.starter.security.principal.TokenExtractor;
import com.foundation.starter.security.snapshot.AuthzChangedListener;
import com.foundation.starter.security.snapshot.AuthzSnapshotClient;
import com.foundation.starter.security.snapshot.AuthzSnapshotFetcher;
import com.foundation.starter.security.snapshot.HttpAuthzSnapshotFetcher;
import com.foundation.starter.security.snapshot.SnapshotCache;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import com.foundation.starter.core.condition.ConditionalOnFoundationRuntimeMode;
import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * 安全接入自动装配（F-07 BAS-08 §1〜§5、§9）。
 *
 * <p>{@code foundation.enabled=false} 时整块不激活；未引入 web 时只失去
 * {@link CurrentUserArgumentResolver} 注册（{@code PrincipalHolder} 与后台任务的 {@code runAs} 仍可用）。
 *
 * <p><b>运行模式约束（CM-03）</b>：本配置的授权快照是经 {@link HttpAuthzSnapshotFetcher}
 * 回源中台的真实 HTTP 出站通道。{@code LOCAL} / {@code SELF_MANAGED} 声明「不连中台」，
 * 因此整块不装配，由接入方自建身份与权限（可自行注册 {@link AuthzSnapshotFetcher} 等 Bean）。
 * 若不作此约束，仅关掉 {@code foundation-starter-client} 的只读客户端仍会留下这条出站链路，
 * 与兼容矩阵 CM-03 冲突。默认 {@code FOUNDATION} 模式下行为不变。
 */
@AutoConfiguration
@ConditionalOnFoundationCapability("security")
@ConditionalOnFoundationRuntimeMode(FoundationRuntimeMode.FOUNDATION)
@EnableConfigurationProperties({FoundationSecurityProperties.class,
        FoundationAuthzProperties.class,
        FoundationDataScopeProperties.class})
public class FoundationSecurityAutoConfiguration {

    // ---------- §2 令牌与当前用户 ----------

    @Bean
    @ConditionalOnMissingBean
    public TokenExtractor tokenExtractor() {
        return new RequestHeaderTokenExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(PrincipalResolver.class)
    public PrincipalResolver principalResolver(FoundationSecurityProperties properties,
                                              TokenExtractor tokenExtractor) {
        if (!"jwt".equalsIgnoreCase(properties.getTokenSource()) || properties.getJwkSetUri() == null) {
            // opaque：内省由应用提供（BAS-20）；未提供即为「无有效令牌」
            return new OpaqueTokenPrincipalResolver(tokenExtractor,
                    OpaqueTokenPrincipalResolver.TokenIntrospector.none());
        }
        JWKSource<SecurityContext> jwkSource = buildJwkSource(properties.getJwkSetUri());
        JWSKeySelector<SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.Family.RSA, jwkSource);
        return new JwtPrincipalResolver(properties, tokenExtractor, keySelector);
    }

    private static JWKSource<SecurityContext> buildJwkSource(String jwkSetUri) {
        try {
            return new RemoteJWKSet<>(URI.create(jwkSetUri).toURL());
        } catch (Exception ex) {
            throw new IllegalStateException("非法的 foundation.security.jwk-set-uri：" + jwkSetUri, ex);
        }
    }

    // ---------- §4 授权快照本地缓存 ----------

    @Bean
    @ConditionalOnMissingBean
    public AuthzSnapshotFetcher authzSnapshotFetcher(ObjectMapper objectMapper,
                                                     FoundationProperties foundationProperties,
                                                     ObjectProvider<BearerTokenSupplier> tokenSupplier) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        return new HttpAuthzSnapshotFetcher(http, objectMapper,
                foundationProperties.getBaseUrl(), tokenSupplier.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthzSnapshotClient authzSnapshotClient(AuthzSnapshotFetcher fetcher,
                                                   FoundationProperties foundationProperties,
                                                   FoundationAuthzProperties authzProperties) {
        FoundationAuthzProperties.Snapshot snapshot = authzProperties.getSnapshot();
        return new SnapshotCache(fetcher,
                foundationProperties.getAppCode(),
                snapshot.getCacheTtl(),
                snapshot.getLocalCache().isEnabled(),
                snapshot.getLocalCache().getTtl(),
                Clock.systemUTC(),
                snapshot.isStaleAllowed(),
                snapshot.getStaleMaxAge());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthzChangedListener authzChangedListener(AuthzSnapshotClient client,
                                                     FoundationProperties foundationProperties,
                                                     ObjectMapper objectMapper) {
        return new AuthzChangedListener(client, foundationProperties.getAppCode(), objectMapper);
    }

    // ---------- §3 权限校验 ----------

    @Bean
    @ConditionalOnMissingBean
    public PermissionAspect permissionAspect(ObjectProvider<AuthzSnapshotClient> snapshotClient,
                                             ObjectProvider<DeniedAuditSink> deniedAuditSink) {
        return new PermissionAspect(snapshotClient.getIfAvailable(),
                deniedAuditSink.getIfAvailable(() -> DeniedAuditSink.NOOP));
    }

    // ---------- §5 数据范围 ----------

    @Bean
    @ConditionalOnMissingBean
    public DataScopeFilterProvider dataScopeFilterProvider() {
        return new DefaultDataScopeFilterProvider();
    }

    /** 引入 spring-webmvc 时注册当前用户参数解析器（F-07 §2）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class WebMvcSecurityConfiguration implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new CurrentUserArgumentResolver());
        }
    }

    /**
     * Servlet 场景下注册请求边界身份过滤器（F-07 §2）：把令牌解析结果写入 {@code PrincipalHolder}，
     * 请求结束清空。早于请求日志（+10）与幂等（+20），使二者都能读到当前身份。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletPrincipalFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public PrincipalAuthenticationFilter principalAuthenticationFilter(PrincipalResolver principalResolver) {
            return new PrincipalAuthenticationFilter(principalResolver);
        }

        @Bean
        @ConditionalOnMissingBean(name = "principalAuthenticationFilterRegistration")
        public FilterRegistrationBean<PrincipalAuthenticationFilter> principalAuthenticationFilterRegistration(
                PrincipalAuthenticationFilter filter) {
            FilterRegistrationBean<PrincipalAuthenticationFilter> registration =
                    new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
            registration.setUrlPatterns(List.of("/*"));
            return registration;
        }
    }
}
