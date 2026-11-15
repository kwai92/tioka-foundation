package com.foundation.starter.web.filter;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程内幂等键存储（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>以 {@code key → 过期毫秒} 保存，用 {@link ConcurrentHashMap#compute} 实现原子判断并在
 * 命中过期项时惰性替换（等价于清理）。不使用 {@code synchronized} / {@code ReentrantLock}（48-CS §4）。
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, Long> expiryByKey = new ConcurrentHashMap<>();

    @Override
    public boolean markIfAbsent(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        long expiry = now + Math.max(ttl.toMillis(), 0L);
        AtomicBoolean first = new AtomicBoolean(false);
        expiryByKey.compute(key, (existingKey, existingExpiry) -> {
            if (existingExpiry == null || existingExpiry <= now) {
                first.set(true);
                return expiry;
            }
            return existingExpiry;
        });
        return first.get();
    }

    @Override
    public void release(String key) {
        expiryByKey.remove(key);
    }
}
