package com.foundation.starter.security.snapshot;

/**
 * 快照回源（F-07 §4、§11）：从 Foundation 取快照与版本号。
 *
 * <p>实现必须**在网络失败时抛异常**（由 {@link SnapshotCache} 决定降级：未过期缓存可用，否则拒绝）。
 */
public interface AuthzSnapshotFetcher {

    /** 取快照；不存在返回 {@code null}。 */
    AuthzSnapshot load(String appCode, String personId);

    /** 取版本号；不存在返回 {@code null}。 */
    Long version(String appCode, String personId);
}
