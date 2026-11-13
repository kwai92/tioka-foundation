package com.foundation.starter.client.cache;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 进程内 TTL 缓存（F-07 §7、§8、§12）。
 *
 * <p>引入 {@code foundation-starter-cache}（Redis）时由应用侧 Redis 取代；未引入时退化为
 * 进程内缓存（TTL 更短，事件订阅由应用自行桥接）。键与 TTL 语义与 Redis 实现同构。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class TtlCache<K, V> {

    private final long ttlMillis;
    private final Clock clock;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TtlCache(long ttlMillis) {
        this(ttlMillis, Clock.systemUTC());
    }

    public TtlCache(long ttlMillis, Clock clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /** 取缓存值；未命中或已过期时用 loader 回源并写入。 */
    public V get(K key, Supplier<V> loader) {
        Entry<V> entry = entries.get(key);
        long now = clock.millis();
        if (entry != null && !entry.expired(now)) {
            return entry.value();
        }
        V value = loader.get();
        if (value != null) {
            entries.put(key, new Entry<>(value, now + ttlMillis));
        } else {
            entries.remove(key);
        }
        return value;
    }

    /** 只读缓存（不回源）。 */
    public Optional<V> peek(K key) {
        Entry<V> entry = entries.get(key);
        long now = clock.millis();
        return entry == null || entry.expired(now) ? Optional.empty() : Optional.of(entry.value());
    }

    /** 失效单个键（事件回调入口）。 */
    public void evict(K key) {
        entries.remove(key);
    }

    /** 按前缀失效（如 {@code fdn:dict:}）。 */
    public void evictByPrefix(String prefix) {
        entries.keySet().removeIf(key -> String.valueOf(key).startsWith(prefix));
    }

    /** 清空。 */
    public void clear() {
        entries.clear();
    }

    /** 当前条目数。 */
    public int size() {
        return entries.size();
    }

    private record Entry<V>(V value, long expiresAtMillis) {

        boolean expired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
