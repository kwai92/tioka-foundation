package com.foundation.starter.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.audit.aspect.AuditedAspect;
import com.foundation.starter.audit.report.AsyncBatchAuditReporter;
import com.foundation.starter.audit.report.AuditDeniedSink;
import com.foundation.starter.audit.report.AuditReporter;
import com.foundation.starter.audit.report.AuditTransport;
import com.foundation.starter.audit.report.HttpAuditTransport;
import com.foundation.starter.audit.spool.DiskSpool;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.http.BearerTokenSupplier;
import com.foundation.starter.security.permission.DeniedAuditSink;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * 审计接入自动装配（F-07 BAS-08 §6）。
 *
 * <p>{@code audit} 依赖 {@code security} 只为取当前用户（S15 §5.2 允许的唯一例外）：
 * 本配置提供 {@link DeniedAuditSink} 实现，接管 {@code PermissionAspect} 的拒绝审计写入。
 */
@AutoConfiguration
@ConditionalOnFoundationCapability("audit")
@EnableConfigurationProperties(FoundationAuditProperties.class)
public class FoundationAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DiskSpool auditDiskSpool(ObjectMapper objectMapper, FoundationAuditProperties properties) {
        return new DiskSpool(Paths.get(properties.getSpoolDir()), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditTransport auditTransport(ObjectMapper objectMapper,
                                         FoundationProperties foundationProperties,
                                         ObjectProvider<BearerTokenSupplier> tokenSupplier) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        return new HttpAuditTransport(http, objectMapper,
                foundationProperties.getBaseUrl(), tokenSupplier.getIfAvailable());
    }

    @Bean(initMethod = "start")
    @ConditionalOnMissingBean
    public AuditReporter auditReporter(AuditTransport transport,
                                       DiskSpool spool,
                                       FoundationAuditProperties properties) {
        return new AsyncBatchAuditReporter(transport, spool,
                properties.getBatchSize(),
                properties.getFlushInterval(),
                properties.getRetry().getMaxAttempts(),
                properties.getRetry().getBaseDelay(),
                properties.getQueueCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    public DeniedAuditSink deniedAuditSink(AuditReporter reporter, FoundationProperties foundationProperties) {
        return new AuditDeniedSink(reporter, foundationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditedAspect auditedAspect(AuditReporter reporter, FoundationProperties foundationProperties) {
        return new AuditedAspect(reporter, foundationProperties);
    }
}
