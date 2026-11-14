package com.foundation.sync.scim.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * SCIM 推送的 {@code Idempotency-Key} 去重（F-08 BAS-13 §3）。
 *
 * <p>同一 {@code Idempotency-Key} 在 TTL 内重复到达时直接返回首次结果，不重复落地。
 * 进程内实现，重启即失效（幂等键 {@code externalId} 仍在主数据层兜底）。
 */
public class ScimIdempotencyStore {

    /** 缓存条目上限，防止无界增长。 */
    private static final int MAX_ENTRIES = 10_000;

    private final Duration ttl;
    private final Supplier<Instant> clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(Object value, Instant at) {
    }

    public ScimIdempotencyStore(Duration ttl) {
        this(ttl, Instant::now);
    }

    public ScimIdempotencyStore(Duration ttl, Supplier<Instant> clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    /** 取首次结果（不存在或已过期返回空）。 */
    public Optional<Object> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        evict();
        Entry entry = entries.get(key);
        return entry == null || expired(entry) ? Optional.empty() : Optional.of(entry.value());
    }

    /** 记录首次结果。 */
    public void put(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (entries.size() >= MAX_ENTRIES) {
            evict();
        }
        entries.put(key, new Entry(value, clock.get()));
    }

    private void evict() {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (expired(iterator.next().getValue())) {
                iterator.remove();
            }
        }
    }

    private boolean expired(Entry entry) {
        return !clock.get().isBefore(entry.at().plus(ttl));
    }
}
