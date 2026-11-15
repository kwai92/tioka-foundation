package com.foundation.starter.observability.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 可观测配置缺省值（F-02 BAS-24 §foundation-starter-observability）。 */
class FoundationObservabilityPropertiesTest {

    @Test
    void holdsDefaults() {
        FoundationObservabilityProperties properties = new FoundationObservabilityProperties();

        assertThat(properties.getTraceSampleRate()).isEqualTo(0.1d);
        assertThat(properties.getServiceName()).isEqualTo("foundation-app");
    }
}
