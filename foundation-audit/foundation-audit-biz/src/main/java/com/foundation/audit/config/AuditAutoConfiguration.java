package com.foundation.audit.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.account.api.AccountApi;
import com.foundation.audit.api.AuditApi;
import com.foundation.audit.api.AuditQueryController;
import com.foundation.audit.api.AuditReportController;
import com.foundation.audit.api.LoginLogApi;
import com.foundation.audit.api.LoginLogController;
import com.foundation.audit.application.AuditArchiver;
import com.foundation.audit.application.AuditRetentionJob;
import com.foundation.audit.application.AuditSealer;
import com.foundation.audit.application.AuditService;
import com.foundation.audit.application.LoginEventCollectJob;
import com.foundation.audit.application.LoginLogService;
import com.foundation.audit.infrastructure.AuditArchiveStore;
import com.foundation.audit.infrastructure.AuditLogRepository;
import com.foundation.audit.infrastructure.AuditReportReceiptRepository;
import com.foundation.audit.infrastructure.AuditSealRepository;
import com.foundation.audit.infrastructure.LoginLogRepository;
import com.foundation.audit.infrastructure.MybatisAuditLogRepository;
import com.foundation.audit.infrastructure.MybatisAuditReportReceiptRepository;
import com.foundation.audit.infrastructure.MybatisAuditSealRepository;
import com.foundation.audit.infrastructure.MybatisLoginLogRepository;
import com.foundation.audit.infrastructure.PostgresPartitionArchiveStore;
import com.foundation.audit.infrastructure.mapper.AuditLogMapper;
import com.foundation.audit.infrastructure.mapper.AuditReportReceiptMapper;
import com.foundation.audit.infrastructure.mapper.AuditSealMapper;
import com.foundation.audit.infrastructure.mapper.LoginLogMapper;
import com.foundation.common.api.keycloak.KeycloakLoginEventAdmin;
import com.foundation.starter.security.snapshot.AuthzSnapshotClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * 审计与登录日志自动装配（F-09 BAS-10）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 应用服务 → {@code -api} 契约 Bean → Controller。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化）与 {@link DataSource}（分区归档 DDL）；任一缺失时整块跳过。
 * {@link KeycloakLoginEventAdmin}（登录事件源）与 {@link AccountApi}（登录名回填）可选，缺失时登录日志采集降级。
 *
 * <p>顺带开启 {@code @EnableScheduling}：登录事件采集 {@link LoginEventCollectJob} 与审计封印 / 归档
 * {@link AuditRetentionJob} 依赖 {@code @Scheduled} 生效。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "audit", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = {
        "com.foundation.starter.data.config.FoundationDataAutoConfiguration",
        "com.foundation.common.config.CommonAutoConfiguration",
        "com.foundation.account.config.AccountAutoConfiguration"})
@ConditionalOnBean({SqlSessionFactory.class})
@EnableConfigurationProperties(AuditProperties.class)
@EnableScheduling
public class AuditAutoConfiguration {

    // ---------- 仓储（MyBatis-Plus） ----------

    @Bean
    @ConditionalOnMissingBean
    public AuditLogRepository auditLogRepository(AuditLogMapper mapper, ObjectMapper objectMapper) {
        return new MybatisAuditLogRepository(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginLogRepository loginLogRepository(LoginLogMapper mapper) {
        return new MybatisLoginLogRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditSealRepository auditSealRepository(AuditSealMapper mapper) {
        return new MybatisAuditSealRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditReportReceiptRepository auditReportReceiptRepository(AuditReportReceiptMapper mapper) {
        return new MybatisAuditReportReceiptRepository(mapper);
    }

    // ---------- 归档存储（分区 DDL，原始 JDBC） ----------

    @Bean
    @ConditionalOnMissingBean
    public AuditArchiveStore auditArchiveStore(DataSource dataSource) {
        return new PostgresPartitionArchiveStore(dataSource);
    }

    // ---------- 应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(AuditLogRepository auditLogs, AuditReportReceiptRepository receipts,
                                     AuditProperties properties) {
        return new AuditService(auditLogs, receipts, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AuditApi.class)
    public AuditApi auditApi(AuditService service) {
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginLogService loginLogService(LoginLogRepository logs,
                                           ObjectProvider<KeycloakLoginEventAdmin> sourceProvider,
                                           ObjectProvider<AccountApi> accountsProvider,
                                           AuditProperties properties) {
        return new LoginLogService(logs, sourceProvider, accountsProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean(LoginLogApi.class)
    public LoginLogApi loginLogApi(LoginLogService service) {
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditSealer auditSealer(AuditLogRepository auditLogs, AuditSealRepository seals) {
        return new AuditSealer(auditLogs, seals);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditArchiver auditArchiver(AuditArchiveStore store, AuditLogRepository auditLogs) {
        return new AuditArchiver(store, auditLogs);
    }

    // ---------- 定时任务 ----------

    @Bean
    @ConditionalOnMissingBean
    public LoginEventCollectJob loginEventCollectJob(LoginLogService loginLogService) {
        return new LoginEventCollectJob(loginLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditRetentionJob auditRetentionJob(AuditSealer sealer, AuditArchiver archiver,
                                               AuditProperties properties) {
        return new AuditRetentionJob(sealer, archiver, properties);
    }

    // ---------- REST 控制器 ----------
    // 与上列 Bean 同生命周期注册：无持久化上下文时整块跳过，避免组件扫描出的控制器装配失败。

    @Bean
    @ConditionalOnMissingBean
    public AuditReportController auditReportController(AuditApi audit) {
        return new AuditReportController(audit);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditQueryController auditQueryController(AuditApi audit,
                                                     ObjectProvider<AuthzSnapshotClient> snapshotProvider) {
        return new AuditQueryController(audit, snapshotProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginLogController loginLogController(LoginLogApi loginLogs) {
        return new LoginLogController(loginLogs);
    }
}
