package com.foundation.starter.cache.key;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 键前缀拼接（F-02 BAS-24 §foundation-starter-cache）。 */
class KeyPrefixTest {

    @Test
    void joinsMultipleParts() {
        KeyPrefix prefix = new KeyPrefix("fdn:");

        assertThat(prefix.of("snapshot", "app", "svc")).isEqualTo("fdn:snapshot:app:svc");
    }

    @Test
    void joinsSinglePart() {
        KeyPrefix prefix = new KeyPrefix("fdn:");

        assertThat(prefix.of("lock")).isEqualTo("fdn:lock");
    }

    @Test
    void returnsBarePrefixWhenNoParts() {
        KeyPrefix prefix = new KeyPrefix("fdn:");

        assertThat(prefix.of()).isEqualTo("fdn:");
        assertThat(prefix.raw()).isEqualTo("fdn:");
    }

    @Test
    void keepsCustomPrefix() {
        KeyPrefix prefix = new KeyPrefix("tioka:");

        assertThat(prefix.of("rate", "login")).isEqualTo("tioka:rate:login");
        assertThat(prefix.raw()).isEqualTo("tioka:");
    }
}
