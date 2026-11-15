package com.foundation.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.account.api.AccountApi;
import com.foundation.account.api.AccountController;
import com.foundation.account.api.ExternalIdentityApi;
import com.foundation.account.api.ExternalIdentityController;
import com.foundation.account.api.OffboardingController;
import com.foundation.account.api.ServiceAccountController;
import com.foundation.account.application.AccountApiAdapter;
import com.foundation.account.application.AccountApplicationService;
import com.foundation.account.application.AccountPushService;
import com.foundation.account.application.AccountReconcileJob;
import com.foundation.account.application.ExternalIdentityApiAdapter;
import com.foundation.account.application.ExternalIdentityApplicationService;
import com.foundation.account.application.HandoverService;
import com.foundation.account.application.OffboardingApplicationService;
import com.foundation.account.application.ServiceAccountApplicationService;
import com.foundation.account.infrastructure.AccountEventOutbox;
import com.foundation.account.infrastructure.AccountPushRepository;
import com.foundation.account.infrastructure.AccountReconcileLock;
import com.foundation.account.infrastructure.AccountReconcileRepository;
import com.foundation.account.infrastructure.AccountRepository;
import com.foundation.account.infrastructure.EmergencyEnableStore;
import com.foundation.account.infrastructure.ExternalIdentityRepository;
import com.foundation.account.infrastructure.HandoverTodoRepository;
import com.foundation.account.infrastructure.MybatisAccountPushRepository;
import com.foundation.account.infrastructure.MybatisAccountReconcileRepository;
import com.foundation.account.infrastructure.MybatisAccountRepository;
import com.foundation.account.infrastructure.MybatisExternalIdentityRepository;
import com.foundation.account.infrastructure.MybatisHandoverTodoRepository;
import com.foundation.account.infrastructure.mapper.AccountMapper;
import com.foundation.account.infrastructure.mapper.AccountPushMapper;
import com.foundation.account.infrastructure.mapper.AccountReconcileMapper;
import com.foundation.account.infrastructure.mapper.ExternalIdentityMapper;
import com.foundation.account.infrastructure.mapper.HandoverTodoMapper;
import com.foundation.app.api.AppApi;
import com.foundation.app.api.ServiceAccountApi;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.api.keycloak.KeycloakUserAdmin;
import com.foundation.org.api.PersonApi;
import com.foundation.org.api.PersonLifecycleApi;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

/**
 * 账号与凭据自动装配（F-04 BAS-03）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 领域服务 → 应用服务 → {@code -api} 契约 Bean。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化）、{@link OutboxAppender}/{@link EventProperties}（事件 outbox）、
 * Keycloak 用户管理（{@link KeycloakUserAdmin}）与 {@link StringRedisTemplate}（对账单实例锁、应急启用申请）；
 * 任一缺失时整块跳过，纯配置上下文（无 DB / 无 Redis）不受影响。
 *
 * <p>顺带开启 {@code @EnableScheduling}：每日对账（{@link AccountReconcileJob}）依赖 {@code @Scheduled} 生效。
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "com.foundation.starter.data.config.FoundationDataAutoConfiguration",
        "com.foundation.common.config.CommonAutoConfiguration"})
@ConditionalOnBean({SqlSessionFactory.class, OutboxAppender.class, EventProperties.class,
        KeycloakUserAdmin.class, StringRedisTemplate.class})
@EnableConfigurationProperties({AccountProperties.class, OffboardingProperties.class})
@EnableScheduling
public class AccountAutoConfiguration {

    // ---------- 仓储（MyBatis-Plus） ----------

    @Bean
    @ConditionalOnMissingBean
    public AccountRepository accountRepository(AccountMapper mapper) {
        return new MybatisAccountRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountPushRepository accountPushRepository(AccountPushMapper mapper) {
        return new MybatisAccountPushRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountReconcileRepository accountReconcileRepository(AccountReconcileMapper mapper) {
        return new MybatisAccountReconcileRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalIdentityRepository externalIdentityRepository(ExternalIdentityMapper mapper) {
        return new MybatisExternalIdentityRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HandoverTodoRepository handoverTodoRepository(HandoverTodoMapper mapper) {
        return new MybatisHandoverTodoRepository(mapper);
    }

    // ---------- 事件与外部依赖适配 ----------

    @Bean
    @ConditionalOnMissingBean
    public AccountEventOutbox accountEventOutbox(OutboxAppender outbox, EventProperties events) {
        return new AccountEventOutbox(outbox, events);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountReconcileLock accountReconcileLock(StringRedisTemplate redis, AccountProperties properties) {
        return new AccountReconcileLock(redis,
                Duration.ofSeconds(properties.getReconcile().getLockTtlSeconds()));
    }

    @Bean
    @ConditionalOnMissingBean
    public EmergencyEnableStore emergencyEnableStore(StringRedisTemplate redis) {
        return new EmergencyEnableStore(redis);
    }

    // ---------- 应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public AccountPushService accountPushService(AccountPushRepository pushes, AccountRepository accounts,
                                                 KeycloakUserAdmin keycloak, ObjectMapper json) {
        return new AccountPushService(pushes, accounts, keycloak, json);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountApplicationService accountApplicationService(AccountRepository accounts, PersonApi persons,
                                                               KeycloakUserAdmin keycloak,
                                                               AccountPushService pushService,
                                                               AccountEventOutbox events,
                                                               EmergencyEnableStore emergencyStore,
                                                               AccountReconcileRepository reconciles,
                                                               AccountProperties properties) {
        return new AccountApplicationService(accounts, persons, keycloak, pushService, events,
                emergencyStore, reconciles, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalIdentityApplicationService externalIdentityApplicationService(
            ExternalIdentityRepository identities, PersonApi persons) {
        return new ExternalIdentityApplicationService(identities, persons);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceAccountApplicationService serviceAccountApplicationService(AccountRepository accounts,
                                                                            PersonApi persons, AppApi apps,
                                                                            ServiceAccountApi serviceAccounts,
                                                                            AccountPushService pushService) {
        return new ServiceAccountApplicationService(accounts, persons, apps, serviceAccounts, pushService);
    }

    // ---------- 停用与离职联动（BAS-14） ----------

    @Bean
    @ConditionalOnMissingBean
    public HandoverService handoverService(HandoverTodoRepository todos, AppApi apps,
                                           ServiceAccountApi serviceAccounts, PersonApi persons,
                                           OffboardingProperties properties) {
        return new HandoverService(todos, apps, serviceAccounts, persons, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OffboardingApplicationService offboardingApplicationService(AccountApplicationService accounts,
                                                                      PersonApi persons,
                                                                      PersonLifecycleApi lifecycle,
                                                                      HandoverService handovers,
                                                                      OffboardingProperties properties) {
        return new OffboardingApplicationService(persons, lifecycle, accounts, handovers, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountReconcileJob accountReconcileJob(AccountRepository accounts, KeycloakUserAdmin keycloak,
                                                   AccountPushService pushService,
                                                   AccountReconcileRepository reconciles,
                                                   AccountReconcileLock lock, AccountProperties properties,
                                                   ObjectMapper json) {
        return new AccountReconcileJob(accounts, keycloak, pushService, reconciles, lock, properties, json);
    }

    // ---------- 对外契约（其他模块依赖） ----------

    @Bean
    @ConditionalOnMissingBean
    public AccountApi accountApi(AccountApplicationService accounts,
                                 ServiceAccountApplicationService serviceAccounts) {
        return new AccountApiAdapter(accounts, serviceAccounts);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalIdentityApi externalIdentityApi(ExternalIdentityApplicationService identities) {
        return new ExternalIdentityApiAdapter(identities);
    }

    // ---------- REST 控制器 ----------
    // 与上列 Bean 同生命周期注册：无持久化上下文时整块跳过，避免组件扫描出的控制器装配失败。

    @Bean
    @ConditionalOnMissingBean
    public AccountController accountController(AccountApplicationService accounts) {
        return new AccountController(accounts);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceAccountController serviceAccountController(ServiceAccountApplicationService serviceAccounts,
                                                             HandoverService handovers) {
        return new ServiceAccountController(serviceAccounts, handovers);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalIdentityController externalIdentityController(
            ExternalIdentityApplicationService identities) {
        return new ExternalIdentityController(identities);
    }

    @Bean
    @ConditionalOnMissingBean
    public OffboardingController offboardingController(OffboardingApplicationService offboarding) {
        return new OffboardingController(offboarding);
    }
}
