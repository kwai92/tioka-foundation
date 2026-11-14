package com.foundation.sync.domain;

import com.foundation.common.id.Ids;
import com.foundation.sync.api.dto.SyncConflictType;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.util.Map;

/**
 * 同步冲突（F-08 BAS-11 §8 {@code fdn_sync_conflict}）：可查、不告警。
 *
 * @param id           冲突 ID（{@code cfl_…}）
 * @param taskId       任务 ID
 * @param sourceId     来源 ID
 * @param objectType   对象类型
 * @param externalId   来源侧标识
 * @param conflictType 冲突类型
 * @param detail       冲突详情
 * @param resolved     是否已处理
 * @param resolvedBy   处理人，可空
 * @param createAt     记录时间
 */
public record SyncConflict(String id,
                           String taskId,
                           String sourceId,
                           SyncObjectType objectType,
                           String externalId,
                           SyncConflictType conflictType,
                           Map<String, Object> detail,
                           boolean resolved,
                           String resolvedBy,
                           Instant createAt) {

    public SyncConflict {
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    /** 生成一条冲突。 */
    public static SyncConflict of(String taskId, String sourceId, SyncObjectType objectType,
                                  String externalId, SyncConflictType conflictType,
                                  Map<String, Object> detail) {
        return new SyncConflict(Ids.newId(SyncIdPrefix.CONFLICT), taskId, sourceId, objectType,
                externalId, conflictType, detail, false, null, Instant.now());
    }

    /** 标记已处理。 */
    public SyncConflict resolvedBy(String operator) {
        return new SyncConflict(id, taskId, sourceId, objectType, externalId, conflictType, detail,
                true, operator, createAt);
    }
}
