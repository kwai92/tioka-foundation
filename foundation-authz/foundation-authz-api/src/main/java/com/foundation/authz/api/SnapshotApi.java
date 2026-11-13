package com.foundation.authz.api;

import java.util.List;
import java.util.Optional;

/**
 * 授权快照契约（F-06 BAS-07 §接口 {@code /api/fdn}）。
 *
 * <p>应用以服务账号调用本契约取快照；starter（BAS-08）在本地缓存并按版本号失效。
 * Foundation 不在请求热路径。
 */
public interface SnapshotApi {

    /** 取单人快照；不存在或快照内容缺失时按需重算回填。 */
    Optional<AuthzSnapshotView> find(String appCode, String personId);

    /** 取单人快照；已知版本号命中时返回空（{@code If-None-Match} 语义，不重算）。 */
    Optional<AuthzSnapshotView> findIfNewer(String appCode, String personId, long knownVersion);

    /** 批量取（供启动预热或对账）。 */
    List<AuthzSnapshotView> batch(String appCode, List<String> personIds);

    /** 只取版本号，供 starter 判缓存是否过期。 */
    Optional<Long> version(String appCode, String personId);

    /** 手动触发重算（幂等）：按人员或按应用。 */
    SnapshotRecomputeResult recompute(SnapshotRecomputeCmd cmd);
}
