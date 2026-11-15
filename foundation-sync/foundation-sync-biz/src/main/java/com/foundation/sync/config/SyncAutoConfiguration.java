package com.foundation.sync.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.sync.api.FieldMappingController;
import com.foundation.sync.api.LocalLockApi;
import com.foundation.sync.api.LocalLockController;
import com.foundation.sync.api.SyncConflictApi;
import com.foundation.sync.api.SyncConflictController;
import com.foundation.sync.api.SyncSourceApi;
import com.foundation.sync.api.SyncSourceController;
import com.foundation.sync.api.SyncTaskApi;
import com.foundation.sync.api.SyncTaskController;
import com.foundation.sync.application.LocalLockService;
import com.foundation.sync.application.SyncConflictService;
import com.foundation.sync.application.SyncEngine;
import com.foundation.sync.application.SyncSourceService;
import com.foundation.sync.application.SyncTaskService;
import com.foundation.sync.domain.DeleteProtectionPolicy;
import com.foundation.sync.domain.DiffPreviewService;
import com.foundation.sync.domain.FieldMapper;
import com.foundation.sync.domain.IdempotentUpserter;
import com.foundation.sync.domain.LocalLockResolver;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.infrastructure.AdapterRegistry;
import com.foundation.sync.infrastructure.MybatisMasterDataPort;
import com.foundation.sync.infrastructure.MybatisSyncConflictRepository;
import com.foundation.sync.infrastructure.MybatisSyncFieldMappingRepository;
import com.foundation.sync.infrastructure.MybatisSyncLogRepository;
import com.foundation.sync.infrastructure.MybatisSyncSourceRepository;
import com.foundation.sync.infrastructure.MybatisSyncTaskRepository;
import com.foundation.sync.infrastructure.NoopSyncLedgerWriter;
import com.foundation.sync.infrastructure.SyncConflictRepository;
import com.foundation.sync.infrastructure.SyncEventOutbox;
import com.foundation.sync.infrastructure.SyncFieldMappingRepository;
import com.foundation.sync.infrastructure.SyncLedgerWriter;
import com.foundation.sync.infrastructure.SyncLogRepository;
import com.foundation.sync.infrastructure.SyncSourceRepository;
import com.foundation.sync.infrastructure.SyncTaskRepository;
import com.foundation.sync.infrastructure.mapper.SyncConflictMapper;
import com.foundation.sync.infrastructure.mapper.SyncExternalIdentityMapper;
import com.foundation.sync.infrastructure.mapper.SyncFieldMappingMapper;
import com.foundation.sync.infrastructure.mapper.SyncLogMapper;
import com.foundation.sync.infrastructure.mapper.SyncOrgUnitMapper;
import com.foundation.sync.infrastructure.mapper.SyncPersonMapper;
import com.foundation.sync.infrastructure.mapper.SyncPositionMapper;
import com.foundation.sync.infrastructure.mapper.SyncSourceMapper;
import com.foundation.sync.infrastructure.mapper.SyncTaskMapper;
import com.foundation.sync.spi.FieldConverter;
import com.foundation.sync.spi.SyncSourceAdapter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 组织与账号同步自动装配（F-08 BAS-11）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 领域规则 → 适配器注册表与主数据落地端口
 * → 应用服务（{@link SyncEngine}）→ {@code -api} 契约 Bean → REST 控制器。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化）与 {@link OutboxAppender}/{@link EventProperties}
 * （事件 outbox）；缺失时整块跳过，纯配置上下文不受影响。
 *
 * <p>适配器（{@link SyncSourceAdapter}）与字段转换函数（{@link FieldConverter}）由插件按需提供，
 * 未装载时注入空集合：能力缺失由引擎标注，不静默跳过。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "sync", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = {
        "com.foundation.starter.data.config.FoundationDataAutoConfiguration",
        "com.foundation.common.config.CommonAutoConfiguration"})
@ConditionalOnBean({SqlSessionFactory.class, OutboxAppender.class, EventProperties.class})
@EnableConfigurationProperties(SyncProperties.class)
public class SyncAutoConfiguration {

    // ---------- 仓储（MyBatis-Plus） ----------

    @Bean
    @ConditionalOnMissingBean
    public SyncSourceRepository syncSourceRepository(SyncSourceMapper mapper) {
        return new MybatisSyncSourceRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncFieldMappingRepository syncFieldMappingRepository(SyncFieldMappingMapper mapper) {
        return new MybatisSyncFieldMappingRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncTaskRepository syncTaskRepository(SyncTaskMapper mapper) {
        return new MybatisSyncTaskRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncLogRepository syncLogRepository(SyncLogMapper mapper) {
        return new MybatisSyncLogRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncConflictRepository syncConflictRepository(SyncConflictMapper mapper) {
        return new MybatisSyncConflictRepository(mapper);
    }

    /** 主数据落地端口：MyBatis-Plus 直连 {@code fdn_person} / {@code fdn_org_unit} 等（不依赖 BAS-02/-03 的 {@code -biz}）。 */
    @Bean
    @ConditionalOnMissingBean
    public MasterDataPort masterDataPort(SyncPersonMapper persons, SyncOrgUnitMapper orgUnits,
                                         SyncPositionMapper positions,
                                         SyncExternalIdentityMapper identities) {
        return new MybatisMasterDataPort(persons, orgUnits, positions, identities);
    }

    // ---------- 领域规则 ----------

    @Bean
    @ConditionalOnMissingBean
    public LocalLockResolver localLockResolver() {
        return new LocalLockResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldMapper fieldMapper(List<FieldConverter> converters) {
        return new FieldMapper(converters);
    }

    @Bean
    @ConditionalOnMissingBean
    public DiffPreviewService diffPreviewService(LocalLockResolver lockResolver) {
        return new DiffPreviewService(lockResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeleteProtectionPolicy deleteProtectionPolicy(SyncProperties properties) {
        SyncProperties.DeleteProtection thresholds = properties.getDeleteProtection();
        return new DeleteProtectionPolicy(thresholds.getDisablePercent(), thresholds.getDisableCount(),
                thresholds.getOrgUnitDeleteCount());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentUpserter idempotentUpserter() {
        return new IdempotentUpserter();
    }

    // ---------- 适配器与出网 ----------

    /** 适配器注册表：按 {@code foundation.sync.adapters} 过滤；未装载任何适配器时为空表。 */
    @Bean
    @ConditionalOnMissingBean
    public AdapterRegistry adapterRegistry(List<SyncSourceAdapter> adapters, SyncProperties properties) {
        return new AdapterRegistry(adapters, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncEventOutbox syncEventOutbox(OutboxAppender outbox, EventProperties eventProperties) {
        return new SyncEventOutbox(outbox, eventProperties);
    }

    /**
     * 出网台账（ADR-074）：默认占位实现。台账表就绪后提供 ADR-074 实现并置
     * {@code foundation.sync.egress.write-ledger=true}。
     */
    @Bean
    @ConditionalOnMissingBean
    public SyncLedgerWriter syncLedgerWriter() {
        return new NoopSyncLedgerWriter();
    }

    // ---------- 应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public SyncEngine syncEngine(SyncTaskRepository tasks, SyncConflictRepository conflicts,
                                 SyncFieldMappingRepository mappings, SyncLogRepository logs,
                                 MasterDataPort masterData, AdapterRegistry adapters, FieldMapper fieldMapper,
                                 DiffPreviewService diffPreview, DeleteProtectionPolicy deleteProtection,
                                 IdempotentUpserter upserter, SyncEventOutbox events,
                                 SyncLedgerWriter ledger) {
        return new SyncEngine(tasks, conflicts, mappings, logs, masterData, adapters, fieldMapper,
                diffPreview, deleteProtection, upserter, events, ledger);
    }

    // ---------- 契约 Bean ----------

    @Bean
    @ConditionalOnMissingBean
    public SyncSourceApi syncSourceApi(SyncSourceRepository sources, SyncFieldMappingRepository mappings,
                                       AdapterRegistry adapters, SyncEngine engine) {
        return new SyncSourceService(sources, mappings, adapters, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncTaskApi syncTaskApi(SyncTaskRepository tasks, SyncLogRepository logs,
                                   SyncSourceRepository sources, SyncEngine engine) {
        return new SyncTaskService(tasks, logs, sources, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncConflictApi syncConflictApi(SyncConflictRepository conflicts) {
        return new SyncConflictService(conflicts);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalLockApi localLockApi(MasterDataPort masterData) {
        return new LocalLockService(masterData);
    }

    // ---------- REST 控制器 ----------
    // 与上列 Bean 同生命周期注册：无持久化上下文（缺 SqlSessionFactory 等）时整块跳过，
    // 避免组件扫描出的控制器在依赖缺失时装配失败。

    @Bean
    @ConditionalOnMissingBean
    public SyncSourceController syncSourceController(SyncSourceApi sources) {
        return new SyncSourceController(sources);
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldMappingController fieldMappingController(SyncSourceApi sources) {
        return new FieldMappingController(sources);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncTaskController syncTaskController(SyncTaskApi tasks) {
        return new SyncTaskController(tasks);
    }

    @Bean
    @ConditionalOnMissingBean
    public SyncConflictController syncConflictController(SyncConflictApi conflicts) {
        return new SyncConflictController(conflicts);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalLockController localLockController(LocalLockApi locks) {
        return new LocalLockController(locks);
    }
}
