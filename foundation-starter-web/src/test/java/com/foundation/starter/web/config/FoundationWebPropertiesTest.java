package com.foundation.starter.web.filter;

import com.foundation.starter.web.config.FoundationWebProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Web 配置缺省值（F-02 BAS-24 §foundation-starter-web）。 */
class FoundationWebPropertiesTest {

    @Test
    void defaultsAreSane() {
        FoundationWebProperties properties = new FoundationWebProperties();

        assertThat(properties.getIdempotencyTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.isRequestLogEnabled()).isTrue();
        assertThat(properties.getSensitiveKeys()).contains("password", "token", "authorization", "client-secret");
    }
}
