package com.foundation.sync.domain;

import com.foundation.common.id.Ids;
import com.foundation.sync.api.dto.SyncAction;
import com.foundation.sync.api.dto.SyncLogLevel;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;

/**
 * 同步日志（F-08 BAS-11 §8 {@code fdn_sync_log}）：只追加的运行明细。
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
public record SyncLog(String id,
                      String taskId,
                      SyncLogLevel level,
                      SyncObjectType objectType,
                      String externalId,
                      SyncAction action,
                      String message,
                      Instant createAt) {

    /** 生成一条日志（ID 与时间自动赋值）。 */
    public static SyncLog of(String taskId, SyncLogLevel level, SyncObjectType objectType,
                             String externalId, SyncAction action, String message) {
        return new SyncLog(Ids.newId(SyncIdPrefix.LOG), taskId, level, objectType, externalId,
                action, message, Instant.now());
    }

    /** 信息级日志。 */
    public static SyncLog info(String taskId, SyncObjectType objectType, String externalId,
                               SyncAction action, String message) {
        return of(taskId, SyncLogLevel.INFO, objectType, externalId, action, message);
    }

    /** 警告级日志（如能力缺失、非权威来源越界）。 */
    public static SyncLog warn(String taskId, SyncObjectType objectType, String externalId,
                               SyncAction action, String message) {
        return of(taskId, SyncLogLevel.WARN, objectType, externalId, action, message);
    }
}
