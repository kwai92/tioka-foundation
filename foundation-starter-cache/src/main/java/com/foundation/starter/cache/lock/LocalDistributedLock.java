package com.foundation.starter.cache.lock;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单 JVM 分布式锁（F-02 BAS-24 §foundation-starter-cache，{@code backend=local}）。
 *
 * <p>用 {@link ConcurrentHashMap} 的 {@code compute} 原子语义实现「到期即抢占」：值记录锁的到期毫秒，
 * 为空或已过期则可写入新租约。不使用 {@code synchronized} / {@code ReentrantLock}。
 *
 * <p>仅保证单实例互斥；跨实例需切 {@code backend=redisson}。
 */
public class LocalDistributedLock implements DistributedLock {

    private final ConcurrentHashMap<String, Long> expirations = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, Duration wait, Duration lease) {
        long leaseMillis = Math.max(1L, lease.toMillis());
        long deadlineNanos = System.nanoTime() + Math.max(0L, wait.toNanos());
        while (true) {
            if (acquire(key, leaseMillis)) {
                return true;
            }
            if (System.nanoTime() >= deadlineNanos) {
                return false;
            }
            sleepQuietly();
        }
    }

    @Override
    public void unlock(String key) {
        expirations.remove(key);
    }

    /** 原子抢占：值为空或已到期才写入新租约。 */
    private boolean acquire(String key, long leaseMillis) {
        long now = System.currentTimeMillis();
        AtomicBoolean acquired = new AtomicBoolean(false);
        expirations.compute(key, (k, expireAt) -> {
            if (expireAt == null || expireAt <= now) {
                acquired.set(true);
                return now + leaseMillis;
            }
            return expireAt;
        });
        return acquired.get();
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
