package com.foundation.starter.client.authz;

import com.foundation.starter.client.integration.SnapshotInvalidationHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 授权快照版本化缓存（P1 运行平面，平台资产，零业务应用依赖）。
 *
 * <p>目的：让中台退出请求热路径（ADR-051 第 5 条）。命中未过期快照时直接返回，
 * 不再调用中台；{@code fdn.authz.changed} 经 {@link AuthorizationSnapshotInvalidationListener}
 * 触发 {@link #invalidate(String)}，下次取用即回源到新版本；事件丢失由 TTL 兜底。
 *
 * <p><b>三条不可放宽的语义</b>：
 * <ol>
 *   <li><b>陈旧拒绝</b>：条目过期或不存在且回源失败/返回 null，一律返回 {@code null}
 *       并删除条目，绝不把过期快照当有效授权使用（身份与授权类不允许 fail-open）；</li>
 *   <li><b>版本不回退</b>：回源拿到的版本号小于已缓存版本（从库延迟、乱序、旧节点），
 *       视为不可用并拒绝，避免权限回退；</li>
 *   <li><b>身份校验</b>：回源结果的 {@code personId} / {@code appCode} 必须与本次请求一致，
 *       否则拒绝，防止网关串号或跨应用快照。</li>
 * </ol>
 *
 * <p>键含 {@code appCode} 与 {@code personId}，同一进程内多个应用互不串缓存。
 * 本实现是进程内缓存；多实例部署时事件广播必须覆盖全部实例，否则仍由 TTL 收敛。
 * 不使用 synchronized / ReentrantLock（48-CS §4），按 key 用 {@link ConcurrentHashMap#compute}
 * 串行回源，顺带避免缓存击穿。
 */
public final class AuthorizationSnapshotCache implements SnapshotInvalidationHook {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationSnapshotCache.class);

    /** 缓存键前缀，与标准路径 {@code SnapshotCache} 的 {@code fdn:snap:} 语义对齐。 */
    static final String KEY_PREFIX = "fdn:snap:client:";

    private final String appCode;
    private final long ttlMillis;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public AuthorizationSnapshotCache(String appCode, Duration ttl) {
        this(appCode, ttl, Clock.systemUTC());
    }

    public AuthorizationSnapshotCache(String appCode, Duration ttl, Clock clock) {
        if (appCode == null || appCode.isBlank()) {
            throw new IllegalArgumentException("appCode is required");
        }
        if (ttl == null || ttl.isNegative()) {
            throw new IllegalArgumentException("snapshot cache ttl must be non-negative");
        }
        this.appCode = appCode;
        this.ttlMillis = ttl.toMillis();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 组成缓存键（应用 + 人员；版本由条目携带并逐次比较）。 */
    public static String cacheKey(String appCode, String personId) {
        return KEY_PREFIX + appCode + ":" + personId;
    }

    /**
     * 取新鲜快照。命中直接返回；未命中/已过期则回源，失败一律返回 {@code null}。
     *
     * @param loader 回源函数，允许返回 {@code null} 或抛运行时异常表示中台不可用
     */
    public AuthorizationSnapshot get(String personId, Supplier<AuthorizationSnapshot> loader) {
        if (personId == null || personId.isBlank() || loader == null) {
            return null;
        }
        Entry result = entries.compute(cacheKey(appCode, personId), (key, current) -> {
            long now = clock.millis();
            if (current != null && !current.expired(now)) {
                return current;
            }
            AuthorizationSnapshot fetched;
            try {
                fetched = loader.get();
            } catch (RuntimeException ex) {
                log.warn("AUDIT foundation.authz.snapshot.deny 缓存未命中且回源异常，拒绝放行："
                                + "appCode={} personId={} cause={}",
                        appCode, personId, ex.getClass().getSimpleName());
                return null;
            }
            if (fetched == null) {
                log.warn("AUDIT foundation.authz.snapshot.deny 缓存未命中且快照不可用，拒绝放行："
                        + "appCode={} personId={}", appCode, personId);
                return null;
            }
            if (!personId.equals(fetched.personId()) || !appCode.equals(fetched.appCode())) {
                log.warn("AUDIT foundation.authz.snapshot.deny 回源身份与请求不一致，拒绝放行："
                                + "appCode={} personId={} returnedApp={} returnedPerson={}",
                        appCode, personId, fetched.appCode(), fetched.personId());
                return null;
            }
            if (current != null && fetched.version() < current.snapshot().version()) {
                log.warn("AUDIT foundation.authz.snapshot.deny 回源版本回退，拒绝放行："
                                + "appCode={} personId={} cached={} fetched={}",
                        appCode, personId, current.snapshot().version(), fetched.version());
                return null;
            }
            return new Entry(fetched, now + ttlMillis);
        });
        return result == null ? null : result.snapshot();
    }

    /**
     * 使指定人员的缓存失效；{@code null} 表示全量失效。
     * 事件驱动的失效是保守操作：宁可多回源一次，也不留陈旧授权。
     */
    @Override
    public void invalidate(String personId) {
        if (personId == null) {
            entries.clear();
            return;
        }
        entries.remove(cacheKey(appCode, personId));
    }

    /** 当前是否有未过期条目（供自检、测试与运维观测）。 */
    public boolean isLive(String personId) {
        Entry entry = entries.get(cacheKey(appCode, personId));
        return entry != null && !entry.expired(clock.millis());
    }

    /** 当前缓存的版本号；无条目返回 {@code null}（含已过期但尚未清理的条目）。 */
    public Long cachedVersion(String personId) {
        Entry entry = entries.get(cacheKey(appCode, personId));
        return entry == null ? null : entry.snapshot().version();
    }

    /** 当前缓存条目数（供观测）。 */
    public int size() {
        return entries.size();
    }

    private record Entry(AuthorizationSnapshot snapshot, long expiresAtMillis) {

        boolean expired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
