package com.foundation.starter.client.authz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 版本化缓存装饰器（P1）：把「每次判定都回源中台」变成「命中新鲜快照零回源」。
 *
 * <p>对接入方完全透明——它实现的仍是 {@link AuthorizationSnapshotClient} 同一个端口，
 * 派生应用与 Tioka 的适配类一行都不用改。缓存策略、失效与陈旧拒绝统一由平台负责，
 * 接入方不得自行声明降级策略（ADR-051 第 3 条「降级策略由平台统一声明」）。
 *
 * <p><b>fail-closed</b>：缓存未命中且中台不可用（返回 {@code null} / 抛异常 / 版本回退）
 * 时返回 {@code null}，由上层判定器按「数据源不可用」拒绝放行；绝不返回过期快照，
 * 也绝不回落到应用本地权限集。
 */
public final class CachingAuthorizationSnapshotClient implements AuthorizationSnapshotClient {

    private static final Logger log = LoggerFactory.getLogger(CachingAuthorizationSnapshotClient.class);

    private final AuthorizationSnapshotCache cache;
    private final HttpAuthorizationSnapshotClient remote;

    public CachingAuthorizationSnapshotClient(AuthorizationSnapshotCache cache,
                                              HttpAuthorizationSnapshotClient remote) {
        if (cache == null || remote == null) {
            throw new IllegalArgumentException("cache and remote client are required");
        }
        this.cache = cache;
        this.remote = remote;
    }

    @Override
    public Set<String> permissionsFor(String personId) {
        if (personId == null || personId.isBlank()) {
            return null;
        }
        AuthorizationSnapshot snapshot;
        try {
            snapshot = cache.get(personId, () -> remote.snapshotFor(personId));
        } catch (RuntimeException ex) {
            log.warn("AUDIT foundation.authz.snapshot.deny 授权快照读取失败，拒绝放行：personId={} cause={}",
                    personId, ex.getClass().getSimpleName());
            return null;
        }
        return snapshot == null ? null : snapshot.permissions();
    }

    /** 暴露缓存供事件监听器与自检报告使用。 */
    public AuthorizationSnapshotCache cache() {
        return cache;
    }
}
