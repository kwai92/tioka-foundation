package com.foundation.server;

import com.foundation.keycloak.KeycloakAdminClient;
import com.foundation.keycloak.KeycloakProperties;
import com.foundation.test.FoundationContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-02 BAS-01：服务账号 {@code client_credentials} 取令牌并读 realm 表示连通（集成层，Testcontainers Keycloak）。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class KeycloakAdminClientIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.registerKeycloak(registry);
    }

    @Autowired
    private KeycloakAdminClient keycloakAdminClient;

    @Autowired
    private KeycloakProperties keycloakProperties;

    @Test
    void obtainsServiceAccountTokenAndReadsRealm() {
        assertThat(keycloakAdminClient.ping()).isTrue();
        assertThat(keycloakAdminClient.accessToken()).isNotBlank();
        assertThat(keycloakAdminClient.realm()).isEqualTo("enterprise");
    }

    @Test
    void bindsContainerProperties() {
        assertThat(keycloakProperties.getServerUrl()).startsWith("http://");
        assertThat(keycloakProperties.getAdminRealm()).isEqualTo(FoundationContainers.KEYCLOAK_ADMIN_REALM);
    }

    @Test
    void reusesCachedTokenUntilExpiry() {
        String first = keycloakAdminClient.accessToken();
        String second = keycloakAdminClient.accessToken();
        assertThat(second).isEqualTo(first);
    }
}
