package com.foundation.starter.client.cache;

import com.foundation.starter.client.config.DegradePolicy;
import com.foundation.starter.client.http.FoundationUnavailableException;
import com.foundation.starter.core.context.DegradeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 进程内 TTL 缓存（F-07 §7、§8、§12；ADP-08 增加降级能力）。
 *
 * <p>引入 {@code foundation-starter-cache}（Redis）时由应用侧 Redis 取代；未引入时退化为
 * 进程内缓存（TTL 更短，事件订阅由应用自行桥接）。键与 TTL 语义与 Redis 实现同构。
 *
 * <p>{@link #get(Object, Supplier)} 保持原有语义：回源失败原样抛出。只读客户端改用
 * {@link #getWithDegrade(Object, Supplier)}：回源抛出运行时异常时按构造时注入的策略处理——
 * 默认 {@code FAIL_CLOSED} 抛 {@link FoundationUnavailableException}；配置
 * {@code STALE_CACHE} 且存在未超过最大陈旧时长的过期条目时，返回过期值、打
 * {@link DegradeContext} 标记并写 WARN 审计日志。
 * <b>回源返回 {@code null} 仍代表「业务空结果」</b>，按原语义返回，不触发降级。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class TtlCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(TtlCache.class);

    private final long ttlMillis;
    private final Clock clock;
    private final DegradePolicy degradePolicy;
    private final long maxStaleMillis;
    private final BiConsumer<K, Long> onStale;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TtlCache(long ttlMillis) {
        this(ttlMillis, Clock.systemUTC());
    }

    public TtlCache(long ttlMillis, Clock clock) {
        this(ttlMillis, clock, DegradePolicy.FAIL_CLOSED, Duration.ofMinutes(30).toMillis());
    }

    public TtlCache(long ttlMillis, Clock clock, DegradePolicy degradePolicy, long maxStaleMillis) {
        this(ttlMillis, clock, degradePolicy, maxStaleMillis, defaultOnStale());
    }

    public TtlCache(long ttlMillis, Clock clock, DegradePolicy degradePolicy,
                    long maxStaleMillis, BiConsumer<K, Long> onStale) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
        this.degradePolicy = degradePolicy == null ? DegradePolicy.FAIL_CLOSED : degradePolicy;
        this.maxStaleMillis = Math.max(0L, maxStaleMillis);
        this.onStale = onStale == null ? defaultOnStale() : onStale;
    }

    /** 取缓存值；未命中或已过期时用 loader 回源并写入（原有语义，不做降级）。 */
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

    /**
     * 可降级的只读取值：回源异常时按策略返回过期缓存或抛出明确异常。
     *
     * @throws FoundationUnavailableException 回源失败且无可用兜底数据
     */
    public V getWithDegrade(K key, Supplier<V> loader) {
        Entry<V> entry = entries.get(key);
        long now = clock.millis();
        if (entry != null && !entry.expired(now)) {
            return entry.value();
        }
        try {
            V value = loader.get();
            if (value != null) {
                entries.put(key, new Entry<>(value, now + ttlMillis));
            } else {
                entries.remove(key);
            }
            return value;
        } catch (RuntimeException ex) {
            return fallback(key, entry, now, ex);
        }
    }

    private V fallback(K key, Entry<V> stale, long now, RuntimeException cause) {
        if (degradePolicy == DegradePolicy.STALE_CACHE && stale != null) {
            long staleMillis = now - stale.expiresAtMillis();
            if (staleMillis <= maxStaleMillis) {
                onStale.accept(key, staleMillis);
                DegradeContext.mark();
                return stale.value();
            }
            log.warn("Foundation 只读能力过期缓存已超出最大陈旧时长，拒绝降级：key={} staleMs={} maxMs={}",
                    key, staleMillis, maxStaleMillis);
        }
        throw new FoundationUnavailableException(String.valueOf(key),
                cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(), cause);
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

    private static <K> BiConsumer<K, Long> defaultOnStale() {
        return (key, staleMillis) -> log.warn(
                "AUDIT foundation.read.degrade 使用过期缓存：key={} staleMs={}（已打降级标记）",
                key, staleMillis);
    }

    private record Entry<V>(V value, long expiresAtMillis) {

        boolean expired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
