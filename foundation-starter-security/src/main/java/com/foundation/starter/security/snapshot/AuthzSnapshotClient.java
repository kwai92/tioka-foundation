package com.foundation.starter.security.snapshot;

/**
 * 取「人员 + 应用」快照（F-07 §4）。
 *
 * <p>实现负责回源（调 Foundation 只读接口）；缓存与降级策略在 {@link SnapshotCache}。
 */
public interface AuthzSnapshotClient {

    /** 取「人员 + 应用」快照；过期且回源失败时抛 {@code FORBIDDEN}（降级拒绝，不返回空）。 */
    AuthzSnapshot load(String personId);

    /** 只取版本号，用于判本地缓存是否过期。 */
    long version(String personId);

    /** 失效本地缓存（{@code fdn.authz.changed} 事件回调入口）。 */
    void evict(String personId);
}
