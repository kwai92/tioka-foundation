package com.foundation.starter.cache.lock;

import com.foundation.starter.cache.key.KeyPrefix;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁（F-02 BAS-24 §foundation-starter-cache，{@code backend=redisson}）。
 *
 * <p>键统一经 {@link KeyPrefix} 加前缀；租约到期由 Redis 自动释放，进程崩溃不会留下死锁。
 * 仅在 {@code org.redisson.api.RedissonClient} 存在时由 {@code FoundationCacheAutoConfiguration}
 * 的 Redisson 装配分支创建。
 */
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redisson;
    private final KeyPrefix keyPrefix;

    public RedissonDistributedLock(RedissonClient redisson, KeyPrefix keyPrefix) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean tryLock(String key, Duration wait, Duration lease) {
        RLock lock = redisson.getLock(keyPrefix.of(key));
        try {
            return lock.tryLock(wait.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        RLock lock = redisson.getLock(keyPrefix.of(key));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
