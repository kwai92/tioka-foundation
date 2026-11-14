package com.foundation.sync.api;

import com.foundation.sync.api.dto.PageResult;
import com.foundation.sync.api.dto.SyncConflictResolveCmd;
import com.foundation.sync.api.dto.SyncConflictView;

/**
 * 同步冲突契约（F-08 BAS-11 §10）：冲突查询与处理。
 */
public interface SyncConflictApi {

    /** {@code GET /api/sync/conflicts?resolved=}：冲突查询（{@code resolved} 为空不过滤）。 */
    PageResult<SyncConflictView> conflicts(Boolean resolved, int page, int size);

    /** {@code POST /api/sync/conflicts/{id}/resolve}：标记冲突已处理。 */
    SyncConflictView resolve(String id, SyncConflictResolveCmd cmd, String operator);
}
