package com.foundation.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.foundation.auth.api.AuthPolicyApi;
import com.foundation.auth.api.AuthPolicyController;
import com.foundation.auth.application.AuthPolicyService;
import com.foundation.auth.infrastructure.AuthPolicyRepository;
import com.foundation.auth.infrastructure.MybatisAuthPolicyRepository;
import com.foundation.auth.infrastructure.mapper.AuthLoginModeMapper;
import com.foundation.auth.infrastructure.mapper.AuthPolicyMapper;
import com.foundation.common.api.keycloak.KeycloakRealmAdmin;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 登录方式与策略自动装配（F-04 BAS-15）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 应用服务 → {@code -api} 契约 Bean。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化）与 {@link KeycloakRealmAdmin}（realm 级下发，BAS-01 提供）；
 * 任一缺失时整块跳过，纯配置上下文（无 DB / 无 Keycloak）不受影响。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "auth", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = {"com.foundation.starter.data.config.FoundationDataAutoConfiguration"})
@ConditionalOnBean({SqlSessionFactory.class, KeycloakRealmAdmin.class})
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuthPolicyRepository authPolicyRepository(AuthPolicyMapper policyMapper,
                                              AuthLoginModeMapper loginModeMapper) {
        return new MybatisAuthPolicyRepository(policyMapper, loginModeMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthPolicyApi authPolicyApi(AuthPolicyRepository repository, KeycloakRealmAdmin realmAdmin,
                                AuthProperties properties) {
        return new AuthPolicyService(repository, realmAdmin, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthPolicyController authPolicyController(AuthPolicyApi api) {
        return new AuthPolicyController(api);
    }
}
