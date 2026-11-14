package com.foundation.sync.application;

import com.foundation.sync.api.dto.DiffItemView;
import com.foundation.sync.api.dto.FieldMappingView;
import com.foundation.sync.api.dto.SyncConflictView;
import com.foundation.sync.api.dto.SyncLogView;
import com.foundation.sync.api.dto.SyncSourceView;
import com.foundation.sync.api.dto.SyncTaskView;
import com.foundation.sync.domain.DiffEntry;
import com.foundation.sync.domain.FieldMapping;
import com.foundation.sync.domain.SyncConflict;
import com.foundation.sync.domain.SyncLog;
import com.foundation.sync.domain.SyncSource;
import com.foundation.sync.domain.SyncTask;

/**
 * 领域对象 → {@code -api} 视图的映射（F-08 BAS-11 §10）。
 *
 * <p>纯结构映射，无业务判断；由 {@code foundation-sync-biz} 的四个应用服务共用，避免视图拼装口径漂移。
 */
final class SyncViews {

    private SyncViews() {
    }

    static SyncSourceView source(SyncSource source) {
        return new SyncSourceView(source.id(), source.code(), source.name(), source.sourceType(),
                source.authority(), source.credentialRef(), source.adapterImpl(), source.pluginSha256(),
                source.capabilities(), source.config(), source.status(), source.scheduleCron(),
                source.updatedAt());
    }

    static SyncTaskView task(SyncTask task, String sourceCode) {
        return new SyncTaskView(task.id(), task.sourceId(), sourceCode, task.mode(), task.triggerMode(),
                task.status(), task.dryRun(), task.createdCount(), task.updatedCount(),
                task.disabledCount(), task.movedCount(), task.capabilityMissing(), task.pauseReason(),
                task.startedAt(), task.finishedAt(), task.createBy());
    }

    static SyncLogView log(SyncLog log) {
        return new SyncLogView(log.id(), log.taskId(), log.level(), log.objectType(), log.externalId(),
                log.action(), log.message(), log.createAt());
    }

    static SyncConflictView conflict(SyncConflict conflict) {
        return new SyncConflictView(conflict.id(), conflict.taskId(), conflict.sourceId(),
                conflict.objectType(), conflict.externalId(), conflict.conflictType(), conflict.detail(),
                conflict.resolved(), conflict.resolvedBy(), conflict.createAt());
    }

    static DiffItemView diffItem(DiffEntry entry) {
        return new DiffItemView(entry.objectType(), entry.externalId(), entry.kind(), entry.fields(),
                entry.parentExternalId(), entry.lockedFields());
    }

    static FieldMappingView fieldMapping(FieldMapping mapping) {
        return new FieldMappingView(mapping.id(), mapping.objectType(), mapping.externalField(),
                mapping.internalField(), mapping.defaultValue(), mapping.transform(), mapping.required());
    }
}
