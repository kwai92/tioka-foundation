package com.foundation.starter.cache.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 单 JVM 固定窗口限流（F-02 BAS-24 §foundation-starter-cache）。 */
class LocalRateLimiterTest {

    private final LocalRateLimiter limiter = new LocalRateLimiter();

    @Test
    void rejectsBeyondWindowLimit() {
        assertThat(limiter.tryAcquire("login", 2, Duration.ofSeconds(10))).isTrue();
        assertThat(limiter.tryAcquire("login", 2, Duration.ofSeconds(10))).isTrue();

        assertThat(limiter.tryAcquire("login", 2, Duration.ofSeconds(10))).isFalse();
    }

    @Test
    void resetsAfterWindowElapsed() throws InterruptedException {
        Duration window = Duration.ofMillis(500);

        assertThat(limiter.tryAcquire("login", 1, window)).isTrue();
        assertThat(limiter.tryAcquire("login", 1, window)).isFalse();

        Thread.sleep(600);

        assertThat(limiter.tryAcquire("login", 1, window)).isTrue();
    }
}
