package com.foundation.server;

import com.foundation.keycloak.KeycloakAdminClient;
import com.foundation.keycloak.KeycloakProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-02 BAS-01：Spring 上下文加载与 {@code foundation.*} 配置绑定（单元层，不起容器）。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class FoundationContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private KeycloakProperties keycloakProperties;

    @Autowired
    private KeycloakAdminClient keycloakAdminClient;

    @Test
    void loadsApplicationContext() {
        assertThat(context.getBean(FoundationApplication.class)).isNotNull();
        assertThat(context.containsBean("keycloakAdminClient")).isTrue();
    }

    @Test
    void bindsKeycloakPropertiesFromApplicationYml() {
        assertThat(keycloakProperties.getServerUrl()).isEqualTo("http://localhost:8180");
        assertThat(keycloakProperties.getRealm()).isEqualTo("enterprise");
        assertThat(keycloakProperties.getAdminRealm()).isEqualTo("master");
        assertThat(keycloakProperties.getClientId()).isEqualTo("foundation-admin");
        assertThat(keycloakProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(keycloakProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void bindsDatabaseAndFlywayProperties() {
        assertThat(environment.getProperty("foundation.flyway.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("foundation.db.url")).startsWith("jdbc:postgresql://");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo(environment.getProperty("foundation.db.url"));
    }

    @Test
    void exposesRealmWithoutCallingKeycloak() {
        assertThat(keycloakAdminClient.realm()).isEqualTo("enterprise");
    }
}
