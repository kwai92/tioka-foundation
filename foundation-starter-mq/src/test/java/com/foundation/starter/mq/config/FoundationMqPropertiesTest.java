package com.foundation.starter.mq.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** {@link FoundationMqProperties} 默认值（F-02 BAS-24 §foundation-starter-mq）。 */
class FoundationMqPropertiesTest {

    @Test
    void 默认值符合约定() {
        FoundationMqProperties properties = new FoundationMqProperties();

        assertEquals("fdn-", properties.getTopicPrefix());
        assertEquals("foundation-producer", properties.getProducerGroup());

        assertFalse(properties.getRelay().isEnabled());
        assertEquals(100, properties.getRelay().getBatchSize());
        assertEquals(Duration.ofSeconds(5), properties.getRelay().getInterval());
        assertEquals(5, properties.getRelay().getMaxRetry());

        assertEquals(24, properties.getConsumer().getRetentionHours());
        assertEquals(100000, properties.getConsumer().getMaxMemoryEntries());
    }
}
