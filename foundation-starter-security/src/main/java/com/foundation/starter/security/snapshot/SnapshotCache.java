package com.foundation.starter.security.snapshot;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.core.context.DegradeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li><b>降级拒绝（默认）</b>：未过期缓存可用；已过期且回源失败 → 抛 {@code FORBIDDEN}，不放行
 *   （F-07 §4、S15 §11.3）；</li>
 *   <li><b>可配的过期放行</b>：显式配置 {@code foundation.authz.snapshot.fail-mode=stale} 时，
 *   在 {@code stale-max-age} 内可返回过期快照，但必须写 WARN 审计日志、打 {@link DegradeContext}
 *   降级标记；身份与授权类默认且建议保持 deny（36 号规划 §6、待决策 D4）。</li>
 * </ul>
 *
 * <p>本类不绑定具体存储：生产环境以 Redis 实现同构键；此处以进程内 Map 承载同一语义，
 * 并保留可选的进程内一级缓存（默认关，TTL ≤ 10 秒）。
 */
public class SnapshotCache implements AuthzSnapshotClient {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCache.class);

    /** 缓存键前缀（F-07 §4）。 */
    static final String KEY_PREFIX = "fdn:snap:";

    private final AuthzSnapshotFetcher fetcher;
    private final String appCode;
    private final Duration ttl;
    private final boolean localCacheEnabled;
    private final Duration localTtl;
    private final Clock clock;
    /** 是否允许在回源失败时返回过期快照（默认 false，身份类不放行）。 */
    private final boolean allowStale;
    /** 过期快照可容忍的最大陈旧时长。 */
    private final long staleMaxAgeMillis;

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
        this(fetcher, appCode, ttl, localCacheEnabled, localTtl, Clock.systemUTC(),
                false, Duration.ofMinutes(10));
    }

    /** 兼容构造器：未声明降级策略时一律 deny（与改造前行为一致）。 */
    public SnapshotCache(AuthzSnapshotFetcher fetcher,
                         String appCode,
                         Duration ttl,
                         boolean localCacheEnabled,
                         Duration localTtl,
                         Clock clock) {
        this(fetcher, appCode, ttl, localCacheEnabled, localTtl, clock,
                false, Duration.ofMinutes(10));
    }

    /** ADP-08：显式传入降级策略与陈旧上限。 */
    public SnapshotCache(AuthzSnapshotFetcher fetcher,
                         String appCode,
                         Duration ttl,
                         boolean localCacheEnabled,
                         Duration localTtl,
                         Clock clock,
                         boolean allowStale,
                         Duration staleMaxAge) {
        this.fetcher = fetcher;
        this.appCode = appCode;
        this.ttl = ttl;
        this.localCacheEnabled = localCacheEnabled;
        this.localTtl = localTtl;
        this.clock = clock;
        this.allowStale = allowStale;
        this.staleMaxAgeMillis = (staleMaxAge == null
                ? Duration.ofMinutes(10) : staleMaxAge).toMillis();
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
        } catch (BizException ex) {
            // 确定性业务拒绝（如 403）不是回源故障，不能用过期快照掩盖后放行；
            // 与 version() 的判定保持一致（授权必须 fail-closed）。
            throw ex;
        } catch (RuntimeException ex) {
            // 已过期（或不存在）且取不到 → 默认降级拒绝；显式配置 stale 时才可放行过期快照
            return staleOrDeny(personId, "授权快照不可用：缓存已过期且回源失败", ex);
        }
        if (fetched == null) {
            return staleOrDeny(personId, "授权快照不可用：缓存已过期且快照不存在", null);
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
            if (version != null) {
                return version;
            }
            return staleVersionOrDeny(personId, "授权快照不可用：缓存已过期且版本取不到", null);
        } catch (BizException ex) {
            // 确定性业务拒绝不降级，避免把 403 掩盖成过期放行
            throw ex;
        } catch (RuntimeException ex) {
            return staleVersionOrDeny(personId, "授权快照不可用：缓存已过期且版本回源失败", ex);
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

    private AuthzSnapshot staleOrDeny(String personId, String message, Throwable cause) {
        AuthzSnapshot stale = staleSnapshot(personId, message, cause);
        if (stale != null) {
            return stale;
        }
        throw deny(message, cause);
    }

    private long staleVersionOrDeny(String personId, String message, Throwable cause) {
        AuthzSnapshot stale = staleSnapshot(personId, message, cause);
        if (stale != null) {
            return stale.version();
        }
        throw deny(message, cause);
    }

    /** 唯一允许身份类「过期放行」的出口：必须留审计、告警与降级标记。 */
    private AuthzSnapshot staleSnapshot(String personId, String message, Throwable cause) {
        if (!allowStale) {
            return null;
        }
        Entry stale = cachedEntry(personId);
        if (stale == null) {
            return null;
        }
        long staleMillis = now() - stale.expiresAtMillis();
        if (staleMillis > staleMaxAgeMillis) {
            log.warn("AUDIT foundation.authz.snapshot.deny 过期快照超出上限，拒绝降级："
                            + "appCode={} personId={} staleMs={} maxMs={}",
                    appCode, personId, staleMillis, staleMaxAgeMillis);
            return null;
        }
        log.warn("AUDIT foundation.authz.snapshot.degrade 授权快照降级放行："
                        + "appCode={} personId={} staleMs={} reason={}",
                appCode, personId, staleMillis,
                message + (cause == null ? "" : "：" + cause.getMessage()));
        DegradeContext.mark();
        return stale.snapshot();
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
