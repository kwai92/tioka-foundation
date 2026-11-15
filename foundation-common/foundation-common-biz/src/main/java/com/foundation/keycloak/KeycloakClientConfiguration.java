package com.foundation.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.keycloak.KeycloakLoginEventAdmin;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import com.foundation.common.api.keycloak.KeycloakRealmAdmin;
import com.foundation.common.api.keycloak.KeycloakUserAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keycloak 管理接口客户端装配（F-02 BAS-01）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeycloakAdminClient keycloakAdminClient(KeycloakProperties properties, ObjectMapper json) {
        return new KeycloakAdminClientImpl(properties, json);
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakOidcClientAdmin keycloakOidcClientAdmin(KeycloakProperties properties,
                                                           KeycloakAdminClient adminClient, ObjectMapper json) {
        return new KeycloakOidcClientAdminImpl(properties, adminClient, json);
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakUserAdmin keycloakUserAdmin(KeycloakProperties properties,
                                               KeycloakAdminClient adminClient, ObjectMapper json) {
        return new KeycloakUserAdminImpl(properties, adminClient, json);
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakLoginEventAdmin keycloakLoginEventAdmin(KeycloakProperties properties,
                                                          KeycloakAdminClient adminClient, ObjectMapper json) {
        return new KeycloakLoginEventAdminImpl(properties, adminClient, json);
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakRealmAdmin keycloakRealmAdmin(KeycloakProperties properties,
                                                 KeycloakAdminClient adminClient, ObjectMapper json) {
        return new KeycloakRealmAdminImpl(properties, adminClient, json);
    }
}
