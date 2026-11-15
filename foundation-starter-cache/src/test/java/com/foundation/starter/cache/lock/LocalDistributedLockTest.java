package com.foundation.starter.cache.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 单 JVM 分布式锁（F-02 BAS-24 §foundation-starter-cache）。 */
class LocalDistributedLockTest {

    private final LocalDistributedLock lock = new LocalDistributedLock();

    @Test
    void firstAttemptAcquires() {
        assertThat(lock.tryLock("job", Duration.ZERO, Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void failsWhileHeldByAnotherHolder() {
        assertThat(lock.tryLock("job", Duration.ZERO, Duration.ofSeconds(5))).isTrue();

        assertThat(lock.tryLock("job", Duration.ZERO, Duration.ofSeconds(5))).isFalse();
    }

    @Test
    void canReacquireAfterUnlock() {
        assertThat(lock.tryLock("job", Duration.ZERO, Duration.ofSeconds(5))).isTrue();

        lock.unlock("job");

        assertThat(lock.tryLock("job", Duration.ZERO, Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void expiredLeaseCanBePreempted() throws InterruptedException {
        assertThat(lock.tryLock("job", Duration.ZERO, Duration.ofMillis(30))).isTrue();

        Thread.sleep(60);

        assertThat(lock.tryLock("job", Duration.ZERO, Duration.ofSeconds(5))).isTrue();
    }
}
