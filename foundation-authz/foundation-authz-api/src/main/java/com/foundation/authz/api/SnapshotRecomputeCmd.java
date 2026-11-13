package com.foundation.authz.api;

import java.util.List;

/**
 * 快照重算请求（F-06 BAS-07 §接口 {@code POST /api/fdn/snapshots:recompute}）。
 *
 * @param appCode   应用编码
 * @param personIds 人员 ID 列表；为空表示按应用全量重算（对已有快照的人员兜底重算）
 */
public record SnapshotRecomputeCmd(String appCode, List<String> personIds) {

    public SnapshotRecomputeCmd {
        personIds = personIds == null ? List.of() : List.copyOf(personIds);
    }
}
