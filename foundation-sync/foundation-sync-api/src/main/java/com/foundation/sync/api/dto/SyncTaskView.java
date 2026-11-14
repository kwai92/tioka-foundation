package com.foundation.sync.api.dto;

import java.time.Instant;

/**
 * 同步任务视图（F-08 BAS-11 §10 {@code GET /api/sync/tasks}）。
 *
 * @param id                任务 ID（{@code syn_…}）
 * @param sourceId          来源 ID
 * @param sourceCode        来源编码
 * @param mode              同步模式
 * @param triggerMode       触发方式
 * @param status            状态
 * @param dryRun            是否差异预演（不落地）
 * @param createdCount      新增计数
 * @param updatedCount      修改计数
 * @param disabledCount     停用计数
 * @param movedCount        移动计数
 * @param capabilityMissing 是否含「能力缺失」标注
 * @param pauseReason       暂停原因，可空
 * @param startedAt         开始时间
 * @param finishedAt        结束时间，可空
 * @param createBy          触发人
 */
public record SyncTaskView(String id,
                           String sourceId,
                           String sourceCode,
                           SyncMode mode,
                           SyncTriggerMode triggerMode,
                           SyncTaskStatus status,
                           boolean dryRun,
                           int createdCount,
                           int updatedCount,
                           int disabledCount,
                           int movedCount,
                           boolean capabilityMissing,
                           SyncPauseReason pauseReason,
                           Instant startedAt,
                           Instant finishedAt,
                           String createBy) {
}
