package com.foundation.starter.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.cache.TtlCache;
import com.foundation.starter.client.dict.DictClient;
import com.foundation.starter.client.dict.DictItem;
import com.foundation.starter.client.dict.HttpDictClient;
import com.foundation.starter.client.http.CircuitBreaker;
import com.foundation.starter.client.http.FoundationHttpClient;
import com.foundation.starter.client.http.ServiceAccountTokenProvider;
import com.foundation.starter.client.file.FileClient;
import com.foundation.starter.client.file.HttpFileClient;
import com.foundation.starter.client.org.HttpOrgClient;
import com.foundation.starter.client.org.HttpPersonClient;
import com.foundation.starter.client.org.OrgClient;
import com.foundation.starter.client.org.OrgUnit;
import com.foundation.starter.client.org.Person;
import com.foundation.starter.client.org.PersonClient;
import com.foundation.starter.client.param.HttpParamClient;
import com.foundation.starter.client.param.ParamClient;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.http.BearerTokenSupplier;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 中台客户端自动装配（F-07 BAS-08 §7、§8、§9、§11）。
 *
 * <p>提供 {@link BearerTokenSupplier}（服务账号令牌）供 {@code security} / {@code audit} 复用，
 * 使三者之间**只依赖 core**（S15 §5.2）。缓存未引入 Redis 时退化为进程内 TTL 缓存（F-07 §12）。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation", name = "enabled", havingValue = "true", matchIfMissing = true)
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
                new TtlCache<String, List<DictItem>>(properties.getDict().getCacheTtl().toMillis()),
                foundationProperties.getAppCode());
    }

    @Bean
    @ConditionalOnMissingBean
    public ParamClient paramClient(FoundationHttpClient http, FoundationClientProperties properties) {
        return new HttpParamClient(http,
                new TtlCache<String, HttpParamClient.ParamEntry>(properties.getParam().getCacheTtl().toMillis()));
    }

    // ---------- §8 组织与人员 ----------

    @Bean
    @ConditionalOnMissingBean
    public OrgClient orgClient(FoundationHttpClient http, FoundationClientProperties properties) {
        long ttl = properties.getOrg().getCacheTtl().toMillis();
        return new HttpOrgClient(http,
                new TtlCache<String, OrgUnit>(ttl),
                new TtlCache<String, List<OrgUnit>>(ttl));
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonClient personClient(FoundationHttpClient http, FoundationClientProperties properties) {
        long ttl = properties.getPerson().getCacheTtl().toMillis();
        return new HttpPersonClient(http,
                new TtlCache<String, Person>(ttl),
                new TtlCache<String, List<Person>>(ttl));
    }

    // ---------- §10 文件 ----------

    @Bean
    @ConditionalOnMissingBean
    public FileClient fileClient(FoundationHttpClient http) {
        return new HttpFileClient(http);
    }
}
