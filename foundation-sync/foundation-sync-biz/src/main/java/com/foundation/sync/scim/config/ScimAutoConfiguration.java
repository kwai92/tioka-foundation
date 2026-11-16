package com.foundation.sync.scim.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.foundation.sync.domain.DeleteProtectionPolicy;
import com.foundation.sync.domain.DiffPreviewService;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.infrastructure.mapper.SyncPersonMapper;
import com.foundation.sync.scim.api.ScimDiscoveryController;
import com.foundation.sync.scim.api.ScimGroupController;
import com.foundation.sync.scim.api.ScimTokenController;
import com.foundation.sync.scim.api.ScimUserController;
import com.foundation.sync.scim.application.ScimGroupService;
import com.foundation.sync.scim.application.ScimIdempotencyStore;
import com.foundation.sync.scim.application.ScimResourceWriter;
import com.foundation.sync.scim.application.ScimTokenService;
import com.foundation.sync.scim.application.ScimUserService;
import com.foundation.sync.scim.domain.ScimDeleteProtection;
import com.foundation.sync.scim.domain.ScimExternalIdentityResolver;
import com.foundation.sync.scim.domain.ScimPersonLookup;
import com.foundation.sync.scim.infrastructure.MybatisScimPersonLookup;
import com.foundation.sync.scim.infrastructure.MybatisScimTokenRepository;
import com.foundation.sync.scim.infrastructure.ScimTokenRepository;
import com.foundation.sync.scim.infrastructure.mapper.ScimTokenMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SCIM 2.0 服务端自动装配（F-08 BAS-13）。
 *
 * <p>装配链路：SCIM 令牌仓储（MyBatis-Plus）→ 领域规则（作用域 / 哈希 / 差异保护 / 外部身份判定）
 * → 应用服务（Users / Groups / 令牌）→ REST 控制器。复用 BAS-11 的
 * {@link MasterDataPort}、{@link DiffPreviewService} 与 {@link DeleteProtectionPolicy}
 * （停用保护把「一次快照」改为「窗口累计」，见 {@link ScimDeleteProtection}）。
 *
 * <p>依赖 {@link SqlSessionFactory} 与 BAS-11 领域 Bean；缺失时整块跳过，纯配置上下文不受影响。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "scim", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = "com.foundation.sync.config.SyncAutoConfiguration")
@ConditionalOnBean({SqlSessionFactory.class, MasterDataPort.class, DiffPreviewService.class,
        DeleteProtectionPolicy.class})
@EnableConfigurationProperties(ScimProperties.class)
public class ScimAutoConfiguration {

    // ---------- 仓储与端口 ----------

    @Bean
    @ConditionalOnMissingBean
    public ScimTokenRepository scimTokenRepository(ScimTokenMapper mapper) {
        return new MybatisScimTokenRepository(mapper);
    }

    /** 外部身份关联的人员查表：仅工号与已验证邮箱哈希（ADR-052 第 3 条）。 */
    @Bean
    @ConditionalOnMissingBean
    public ScimPersonLookup scimPersonLookup(SyncPersonMapper persons) {
        return new MybatisScimPersonLookup(persons);
    }

    // ---------- 领域规则 ----------

    @Bean
    @ConditionalOnMissingBean
    public ScimExternalIdentityResolver scimExternalIdentityResolver() {
        return new ScimExternalIdentityResolver();
    }

    /** 窗口累计差异保护：复用 BAS-11 阈值，把「一次快照」改为「窗口累计」（F-08 BAS-13 §4）。 */
    @Bean
    @ConditionalOnMissingBean
    public ScimDeleteProtection scimDeleteProtection(DeleteProtectionPolicy policy, ScimProperties properties) {
        return new ScimDeleteProtection(policy, properties.getDeactivateWindow());
    }

    // ---------- 应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public ScimIdempotencyStore scimIdempotencyStore(ScimProperties properties) {
        return new ScimIdempotencyStore(properties.getIdempotencyTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimTokenService scimTokenService(ScimTokenRepository tokens) {
        return new ScimTokenService(tokens);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimResourceWriter scimResourceWriter(MasterDataPort masterData, DiffPreviewService diffPreview) {
        return new ScimResourceWriter(masterData, diffPreview);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimUserService scimUserService(MasterDataPort masterData, ScimResourceWriter writer,
                                           ScimExternalIdentityResolver resolver, ScimPersonLookup lookup,
                                           ScimDeleteProtection protection, ScimIdempotencyStore idempotency) {
        return new ScimUserService(masterData, writer, resolver, lookup, protection, idempotency);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimGroupService scimGroupService(MasterDataPort masterData, ScimResourceWriter writer,
                                             ScimDeleteProtection protection, ScimIdempotencyStore idempotency) {
        return new ScimGroupService(masterData, writer, protection, idempotency);
    }

    // ---------- REST 控制器 ----------

    @Bean
    @ConditionalOnMissingBean
    public ScimUserController scimUserController(ScimUserService users, ScimTokenService tokens) {
        return new ScimUserController(users, tokens);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimGroupController scimGroupController(ScimGroupService groups, ScimTokenService tokens) {
        return new ScimGroupController(groups, tokens);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimDiscoveryController scimDiscoveryController(ScimTokenService tokens) {
        return new ScimDiscoveryController(tokens);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimTokenController scimTokenController(ScimTokenService tokens) {
        return new ScimTokenController(tokens);
    }
}
