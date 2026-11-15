package com.foundation.starter.cache.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 缓存配置缺省值（F-02 BAS-24 §foundation-starter-cache）。 */
class FoundationCachePropertiesTest {

    @Test
    void holdsDefaults() {
        FoundationCacheProperties properties = new FoundationCacheProperties();

        assertThat(properties.getKeyPrefix()).isEqualTo("fdn:");
        assertThat(properties.getBackend()).isEqualTo("local");
        assertThat(properties.getLockWait()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getLockLease()).isEqualTo(Duration.ofSeconds(30));
    }
}
