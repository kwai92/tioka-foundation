package com.foundation.sync.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.api.SyncConflictApi;
import com.foundation.sync.api.dto.PageResult;
import com.foundation.sync.api.dto.SyncConflictResolveCmd;
import com.foundation.sync.api.dto.SyncConflictView;
import com.foundation.sync.domain.SyncConflict;
import com.foundation.sync.infrastructure.SyncConflictRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 同步冲突应用服务（F-08 BAS-11 §10）：冲突查询与处理。
 *
 * <p>冲突「可查、不告警」（§11），只做查询与标记已处理，不触发重跑。
 */
public class SyncConflictService implements SyncConflictApi {

    private final SyncConflictRepository conflicts;

    public SyncConflictService(SyncConflictRepository conflicts) {
        this.conflicts = conflicts;
    }

    @Override
    public PageResult<SyncConflictView> conflicts(Boolean resolved, int page, int size) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 20 : size;
        List<SyncConflictView> items = conflicts.page(resolved, safePage, safeSize).stream()
                .map(SyncViews::conflict).toList();
        return PageResult.of(items, conflicts.count(resolved), safePage, safeSize);
    }

    @Override
    @Transactional
    public SyncConflictView resolve(String id, SyncConflictResolveCmd cmd, String operator) {
        SyncConflict conflict = conflicts.find(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "同步冲突不存在: " + id));
        if (conflict.resolved()) {
            return SyncViews.conflict(conflict);
        }
        conflicts.markResolved(id, operator);
        return SyncViews.conflict(conflict.resolvedBy(operator));
    }
}
