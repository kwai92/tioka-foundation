package com.foundation.sync.api.dto;

/** 同步任务状态（{@code fdn_sync_task.status}）。 */
public enum SyncTaskStatus {

    RUNNING,

    /** 超阈值暂停，等管理员确认（{@code pause_reason=THRESHOLD}）。 */
    PAUSED,

    SUCCEEDED,

    FAILED,

    /** 部分成功（含能力缺失或部分条目失败）。 */
    PARTIAL;

    /** 是否处于终态。 */
    public boolean isTerminal() {
        return this != RUNNING && this != PAUSED;
    }

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncTaskStatus of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncTaskStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
