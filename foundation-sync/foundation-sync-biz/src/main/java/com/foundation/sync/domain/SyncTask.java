package com.foundation.sync.domain;

import com.foundation.sync.api.dto.SyncMode;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.api.dto.SyncTriggerMode;
import com.foundation.sync.spi.IncrementalCursor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步任务（F-08 BAS-11 §8 {@code fdn_sync_task}）：一次同步 / 预演 / 对账的运行记录。
 *
 * @param id                任务 ID（{@code syn_…}）
 * @param sourceId          来源 ID
 * @param mode              同步模式
 * @param triggerMode       触发方式
 * @param status            状态
 * @param dryRun            是否差异预演（不落地）
 * @param cursor            增量游标（{@code cursor_json}）
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
public record SyncTask(String id,
                       String sourceId,
                       SyncMode mode,
                       SyncTriggerMode triggerMode,
                       SyncTaskStatus status,
                       boolean dryRun,
                       IncrementalCursor cursor,
                       int createdCount,
                       int updatedCount,
                       int disabledCount,
                       int movedCount,
                       boolean capabilityMissing,
                       SyncPauseReason pauseReason,
                       Instant startedAt,
                       Instant finishedAt,
                       String createBy) {

    /** 计数摘要（事件与报告用，字段顺序稳定）。 */
    public Map<String, Integer> counts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("created", createdCount);
        counts.put("updated", updatedCount);
        counts.put("disabled", disabledCount);
        counts.put("moved", movedCount);
        return counts;
    }

    /** 记录计数与能力缺失标注（落地或预演结束后）。 */
    public SyncTask withCounts(DiffResult diff, boolean missingCapability) {
        return new SyncTask(id, sourceId, mode, triggerMode, status, dryRun, cursor,
                diff.countCreated(), diff.countUpdated(), diff.countDisabled(), diff.countMoved(),
                missingCapability, pauseReason, startedAt, finishedAt, createBy);
    }

    /** 状态流转。 */
    public SyncTask withStatus(SyncTaskStatus newStatus, SyncPauseReason newPauseReason, Instant finished) {
        return new SyncTask(id, sourceId, mode, triggerMode, newStatus, dryRun, cursor,
                createdCount, updatedCount, disabledCount, movedCount, capabilityMissing,
                newPauseReason, startedAt, finished, createBy);
    }
}
