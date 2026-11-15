package com.foundation.starter.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.cache.TtlCache;
import com.foundation.starter.client.calendar.CalendarClient;
import com.foundation.starter.client.calendar.HttpCalendarClient;
import com.foundation.starter.client.dict.DictClient;
import com.foundation.starter.client.dict.HttpDictClient;
import com.foundation.starter.client.http.CircuitBreaker;
import com.foundation.starter.client.http.FoundationHttpClient;
import com.foundation.starter.client.http.ServiceAccountTokenProvider;
import com.foundation.starter.client.file.FileClient;
import com.foundation.starter.client.file.HttpFileClient;
import com.foundation.starter.client.org.HttpOrgClient;
import com.foundation.starter.client.org.HttpPersonClient;
import com.foundation.starter.client.org.OrgClient;
import com.foundation.starter.client.org.PersonClient;
import com.foundation.starter.client.param.HttpParamClient;
import com.foundation.starter.client.param.ParamClient;
import com.foundation.starter.client.sequence.HttpSequenceClient;
import com.foundation.starter.client.sequence.SequenceClient;
import com.foundation.starter.core.condition.ConditionalOnFoundationRuntimeMode;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.http.BearerTokenSupplier;
import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * 中台客户端自动装配（F-07 BAS-08 §7、§8、§9、§11）。
 *
 * <p>提供 {@link BearerTokenSupplier}（服务账号令牌）供 {@code security} / {@code audit} 复用，
 * 使三者之间**只依赖 core**（S15 §5.2）。缓存未引入 Redis 时退化为进程内 TTL 缓存（F-07 §12）。
 *
 * <p>ADP-08 起只在 {@link FoundationRuntimeMode#FOUNDATION}（缺省）模式装配；
 * {@code LOCAL} / {@code SELF_MANAGED} 由 {@link LocalFoundationClientAutoConfiguration}
 * 接管，避免本地模式仍保留指向中台的出站通道。
 */
@AutoConfiguration
@ConditionalOnFoundationCapability("client")
@ConditionalOnFoundationRuntimeMode(FoundationRuntimeMode.FOUNDATION)
@EnableConfigurationProperties(FoundationClientProperties.class)
public class FoundationClientAutoConfiguration {

    // ---------- §11 出站与令牌 ----------

    @Bean
    @ConditionalOnMissingBean(name = "foundationOkHttpClient")
    public OkHttpClient foundationOkHttpClient(FoundationClientProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getClient().getConnectTimeout())
                .readTimeout(properties.getClient().getReadTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreaker foundationCircuitBreaker(FoundationClientProperties properties) {
        FoundationClientProperties.CircuitBreaker breaker = properties.getClient().getCircuitBreaker();
        return new CircuitBreaker(breaker.getFailureRate(), breaker.getMinCalls(),
                breaker.getWindow().toMillis(), breaker.getOpenDuration().toMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public FoundationHttpClient foundationHttpClient(OkHttpClient foundationOkHttpClient,
                                                     ObjectMapper objectMapper,
                                                     FoundationProperties foundationProperties,
                                                     CircuitBreaker foundationCircuitBreaker,
                                                     FoundationClientProperties properties) {
        FoundationClientProperties.Client client = properties.getClient();
        return new FoundationHttpClient(foundationOkHttpClient, objectMapper,
                foundationProperties.getBaseUrl(), null, foundationCircuitBreaker,
                client.getRetry().getMaxAttempts(), client.getRetry().getBaseDelay().toMillis());
    }

    /** 服务账号令牌：供本 starter 与 {@code security} / {@code audit} 复用（F-07 §11）。 */
    @Bean
    @ConditionalOnMissingBean
    public BearerTokenSupplier serviceAccountTokenProvider(OkHttpClient foundationOkHttpClient,
                                                           ObjectMapper objectMapper,
                                                           FoundationProperties foundationProperties) {
        return new ServiceAccountTokenProvider(foundationOkHttpClient, objectMapper,
                foundationProperties.getServiceAccount());
    }

    // ---------- §7 字典与参数 ----------

    @Bean
    @ConditionalOnMissingBean
    public DictClient dictClient(FoundationHttpClient http,
                                 FoundationClientProperties properties,
                                 FoundationProperties foundationProperties) {
        return new HttpDictClient(http,
                cache(properties, "dict", properties.getDict().getCacheTtl().toMillis()),
                foundationProperties.getAppCode());
    }

    @Bean
    @ConditionalOnMissingBean
    public ParamClient paramClient(FoundationHttpClient http, FoundationClientProperties properties) {
        return new HttpParamClient(http,
                cache(properties, "param", properties.getParam().getCacheTtl().toMillis()));
    }

    // ---------- §8 组织与人员 ----------

    @Bean
    @ConditionalOnMissingBean
    public OrgClient orgClient(FoundationHttpClient http, FoundationClientProperties properties) {
        long ttl = properties.getOrg().getCacheTtl().toMillis();
        return new HttpOrgClient(http,
                cache(properties, "org", ttl),
                cache(properties, "org", ttl));
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonClient personClient(FoundationHttpClient http, FoundationClientProperties properties) {
        long ttl = properties.getPerson().getCacheTtl().toMillis();
        return new HttpPersonClient(http,
                cache(properties, "person", ttl),
                cache(properties, "person", ttl));
    }

    // ---------- §10 文件 ----------

    @Bean
    @ConditionalOnMissingBean
    public FileClient fileClient(FoundationHttpClient http) {
        return new HttpFileClient(http);
    }

    // ---------- F-09 §18 工作日历与编号 ----------

    @Bean
    @ConditionalOnMissingBean
    public CalendarClient calendarClient(FoundationHttpClient http) {
        return new HttpCalendarClient(http);
    }

    @Bean
    @ConditionalOnMissingBean
    public SequenceClient sequenceClient(FoundationHttpClient http) {
        return new HttpSequenceClient(http);
    }

    /**
     * 按能力注入只读缓存的降级策略（ADP-08）。
     *
     * <p>白名单外的能力（如 {@code security} / {@code authz}）无论全局配置如何都得到
     * {@link DegradePolicy#FAIL_CLOSED}，身份与授权类不允许 fail-open。
     */
    private static <V> TtlCache<String, V> cache(FoundationClientProperties properties,
                                                 String capability, long ttlMillis) {
        FoundationClientProperties.Degrade degrade = properties.getClient().getDegrade();
        return new TtlCache<>(ttlMillis, Clock.systemUTC(),
                degrade.policyFor(capability), degrade.maxStaleMillis());
    }
}
