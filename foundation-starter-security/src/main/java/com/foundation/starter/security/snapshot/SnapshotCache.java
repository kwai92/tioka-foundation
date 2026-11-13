package com.foundation.starter.security.snapshot;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 授权快照本地缓存（F-07 §4）。
 *
 * <p>策略：
 * <ul>
 *   <li><b>粒度</b>：一个人员在一个应用里一份快照；</li>
 *   <li><b>键</b>：{@code fdn:snap:{appCode}:{personId}:{version}}，含版本号，避免版本漂移；</li>
 *   <li><b>失效</b>：{@code fdn.authz.changed} 事件 → {@link #evict(String)}；</li>
 *   <li><b>TTL 兜底</b>：默认 5 分钟，事件丢失时到期强制回源；</li>
 *   <li><b>降级拒绝</b>：未过期缓存可用；<b>已过期且回源失败 → 抛 {@code FORBIDDEN}，不放行</b>。</li>
 * </ul>
 *
 * <p>本类不绑定具体存储：生产环境以 Redis 实现同构键；此处以进程内 Map 承载同一语义，
 * 并保留可选的进程内一级缓存（默认关，TTL ≤ 10 秒）。
 */
public class SnapshotCache implements AuthzSnapshotClient {

    /** 缓存键前缀（F-07 §4）。 */
    static final String KEY_PREFIX = "fdn:snap:";

    private final AuthzSnapshotFetcher fetcher;
    private final String appCode;
    private final Duration ttl;
    private final boolean localCacheEnabled;
    private final Duration localTtl;
    private final Clock clock;

    /** 已缓存快照，键为完整缓存键（含版本号）。 */
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** 人员 → 当前缓存键索引。 */
    private final Map<String, String> keyByPerson = new ConcurrentHashMap<>();

    /** 进程内一级缓存（默认关）。 */
    private final Map<String, LocalEntry> localEntries = new ConcurrentHashMap<>();

    public SnapshotCache(AuthzSnapshotFetcher fetcher,
                         String appCode,
                         Duration ttl,
                         boolean localCacheEnabled,
                         Duration localTtl) {
        this(fetcher, appCode, ttl, localCacheEnabled, localTtl, Clock.systemUTC());
    }

    public SnapshotCache(AuthzSnapshotFetcher fetcher,
                         String appCode,
                         Duration ttl,
                         boolean localCacheEnabled,
                         Duration localTtl,
                         Clock clock) {
        this.fetcher = fetcher;
        this.appCode = appCode;
        this.ttl = ttl;
        this.localCacheEnabled = localCacheEnabled;
        this.localTtl = localTtl;
        this.clock = clock;
    }

    /** 组成缓存键（含版本号）。 */
    public static String cacheKey(String appCode, String personId, long version) {
        return KEY_PREFIX + appCode + ":" + personId + ":" + version;
    }

    @Override
    public AuthzSnapshot load(String personId) {
        AuthzSnapshot local = readLocal(personId);
        if (local != null) {
            return local;
        }
        Entry cached = liveEntry(personId);
        if (cached != null) {
            return cached.snapshot();
        }
        AuthzSnapshot fetched;
        try {
            fetched = fetcher.load(appCode, personId);
        } catch (RuntimeException ex) {
            // 已过期（或不存在）且取不到 → 降级拒绝，不放行（F-07 §4）
            throw deny("授权快照不可用：缓存已过期且回源失败", ex);
        }
        if (fetched == null) {
            throw deny("授权快照不可用：缓存已过期且快照不存在", null);
        }
        put(fetched);
        return fetched;
    }

    @Override
    public long version(String personId) {
        Entry cached = liveEntry(personId);
        if (cached != null) {
            return cached.snapshot().version();
        }
        try {
            Long version = fetcher.version(appCode, personId);
            if (version == null) {
                throw deny("授权快照不可用：缓存已过期且版本取不到", null);
            }
            return version;
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw deny("授权快照不可用：缓存已过期且版本回源失败", ex);
        }
    }

    @Override
    public void evict(String personId) {
        localEntries.remove(personId);
        String key = keyByPerson.remove(personId);
        if (key != null) {
            entries.remove(key);
        }
    }

    /** 该人员是否有未过期缓存（供测试与运维观测）。 */
    public boolean isCached(String personId) {
        return liveEntry(personId) != null;
    }

    /** 当前缓存条目数（供测试与运维观测）。 */
    public int cachedCount() {
        return entries.size();
    }

    private void put(AuthzSnapshot snapshot) {
        String key = cacheKey(appCode, snapshot.personId(), snapshot.version());
        String previousKey = keyByPerson.put(snapshot.personId(), key);
        if (previousKey != null && !previousKey.equals(key)) {
            // 版本号变化：丢弃旧版本条目，避免串缓存
            entries.remove(previousKey);
        }
        entries.put(key, new Entry(snapshot, now() + ttl.toMillis()));
        if (localCacheEnabled) {
            localEntries.put(snapshot.personId(),
                    new LocalEntry(snapshot, now() + localTtl.toMillis()));
        }
    }

    private Entry liveEntry(String personId) {
        Entry entry = cachedEntry(personId);
        return entry == null || entry.expired(now()) ? null : entry;
    }

    private Entry cachedEntry(String personId) {
        String key = keyByPerson.get(personId);
        return key == null ? null : entries.get(key);
    }

    private AuthzSnapshot readLocal(String personId) {
        if (!localCacheEnabled) {
            return null;
        }
        LocalEntry entry = localEntries.get(personId);
        if (entry == null) {
            return null;
        }
        if (entry.expired(now())) {
            localEntries.remove(personId);
            return null;
        }
        return entry.snapshot();
    }

    private long now() {
        return clock.millis();
    }

    private static BizException deny(String message, Throwable cause) {
        return cause == null
                ? new BizException(ErrorCode.FORBIDDEN, message)
                : new BizException(ErrorCode.FORBIDDEN, message + "：" + cause.getMessage());
    }

    private record Entry(AuthzSnapshot snapshot, long expiresAtMillis) {

        boolean expired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }

    private record LocalEntry(AuthzSnapshot snapshot, long expiresAtMillis) {

        boolean expired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
