package com.foundation.sync.api.dto;

import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.util.Map;

/**
 * 同步冲突视图（F-08 BAS-11 §10 {@code GET /api/sync/conflicts}）。
 *
 * @param id           冲突 ID（{@code cfl_…}）
 * @param taskId       任务 ID
 * @param sourceId     来源 ID
 * @param objectType   对象类型
 * @param externalId   来源侧标识
 * @param conflictType 冲突类型
 * @param detail       冲突详情（如被锁字段、越界对象）
 * @param resolved     是否已处理
 * @param resolvedBy   处理人，可空
 * @param createAt     记录时间
 */
public record SyncConflictView(String id,
                               String taskId,
                               String sourceId,
                               SyncObjectType objectType,
                               String externalId,
                               SyncConflictType conflictType,
                               Map<String, Object> detail,
                               boolean resolved,
                               String resolvedBy,
                               Instant createAt) {
}
