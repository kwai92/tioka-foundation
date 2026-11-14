package com.foundation.sync.api.dto;

/** 同步任务暂停原因（{@code fdn_sync_task.pause_reason}）。 */
public enum SyncPauseReason {

    /** 删除保护阈值触发（停用 > 5% 或 > 50 人、删除组织 > 10 个）。 */
    THRESHOLD;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncPauseReason of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncPauseReason reason : values()) {
            if (reason.name().equalsIgnoreCase(value.trim())) {
                return reason;
            }
        }
        return null;
    }
}
