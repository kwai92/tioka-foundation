package com.foundation.sync.api.dto;

import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;

/**
 * 同步日志视图（F-08 BAS-11 §10 {@code GET /api/sync/tasks/{id}/logs}）。
 *
 * @param id         日志 ID（{@code slg_…}）
 * @param taskId     任务 ID
 * @param level      级别
 * @param objectType 对象类型，可空
 * @param externalId 来源侧标识，可空
 * @param action     动作，可空
 * @param message    说明
 * @param createAt   记录时间
 */
public record SyncLogView(String id,
                          String taskId,
                          SyncLogLevel level,
                          SyncObjectType objectType,
                          String externalId,
                          SyncAction action,
                          String message,
                          Instant createAt) {
}
