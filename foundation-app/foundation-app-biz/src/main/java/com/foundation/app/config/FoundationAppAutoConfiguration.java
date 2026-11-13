package com.foundation.app.config;

import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppClientApi;
import com.foundation.app.api.ServiceAccountApi;
import com.foundation.app.application.AppRegistrar;
import com.foundation.app.application.ClientSecretRotator;
import com.foundation.app.application.ServiceAccountService;
import com.foundation.app.infrastructure.AppClientRepository;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.app.infrastructure.JdbcAppClientRepository;
import com.foundation.app.infrastructure.JdbcAppRepository;
import com.foundation.app.infrastructure.JdbcServiceAccountRepository;
import com.foundation.app.infrastructure.ServiceAccountRepository;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 应用登记自动装配（F-05 BAS-04）。
 *
 * <p>依赖 {@link JdbcTemplate} 与 Keycloak OIDC 客户端管理（{@code foundation-common-biz} 提供实现）；
 * 两者缺失时本自动装配不生效，纯配置上下文不受影响。
 */
@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@ConditionalOnBean({JdbcTemplate.class, KeycloakOidcClientAdmin.class})
@EnableConfigurationProperties(AppProperties.class)
public class FoundationAppAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AppRepository appRepository(JdbcTemplate jdbc) {
        return new JdbcAppRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppClientRepository appClientRepository(JdbcTemplate jdbc) {
        return new JdbcAppClientRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceAccountRepository serviceAccountRepository(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcServiceAccountRepository(jdbc, json);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppApi appApi(AppRepository apps, AppClientRepository clients, KeycloakOidcClientAdmin keycloak) {
        return new AppRegistrar(apps, clients, keycloak);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppClientApi appClientApi(AppRepository apps, AppClientRepository clients,
                                     KeycloakOidcClientAdmin keycloak) {
        return new ClientSecretRotator(apps, clients, keycloak);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceAccountApi serviceAccountApi(AppRepository apps, ServiceAccountRepository accounts,
                                               KeycloakOidcClientAdmin keycloak, AppProperties properties) {
        return new ServiceAccountService(apps, accounts, keycloak, properties);
    }
}
