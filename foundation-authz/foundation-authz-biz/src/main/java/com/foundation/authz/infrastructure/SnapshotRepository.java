package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.AuthzSnapshot;

import java.util.List;
import java.util.Optional;

/** 授权快照仓储（F-06 BAS-07 §数据模型）。 */
public interface SnapshotRepository {

    /** 快照版本号；不存在返回空。 */
    Optional<Long> version(String personId, String appCode);

    /** 快照内容；不存在或内容缺失（{@code body} 为空）时返回空，由调用方按需重算回填。 */
    Optional<AuthzSnapshot> find(String personId, String appCode);

    /** 该应用已有快照的人员 ID（按应用全量重算的兜底集合）。 */
    List<String> listPersonsByApp(String appCode);

    /** 写入或更新快照（按 {@code (personId, appCode)} 唯一）。 */
    void upsert(AuthzSnapshot snapshot);
}
