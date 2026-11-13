package com.foundation.common.dict.application;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 服务端读取缓存（F-02 BAS-09 配置项 {@code foundation.dict.cache-ttl}）。
 * TTL 为零或负值表示不缓存；写操作按 key 失效。
 */
final class TtlCache<V> {

    private final Duration ttl;
    private final Map<String, Entry<V>> entries = new ConcurrentHashMap<>();

    TtlCache(Duration ttl) {
        this.ttl = ttl;
    }

    V get(String key, Supplier<V> loader) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return loader.get();
        }
        long now = System.nanoTime();
        Entry<V> cached = entries.get(key);
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.value();
        }
        V value = loader.get();
        if (value != null) {
            entries.put(key, new Entry<>(value, now + ttl.toNanos()));
        }
        return value;
    }

    void invalidate(String key) {
        entries.remove(key);
    }

    private record Entry<V>(V value, long expiresAtNanos) {
    }
}
