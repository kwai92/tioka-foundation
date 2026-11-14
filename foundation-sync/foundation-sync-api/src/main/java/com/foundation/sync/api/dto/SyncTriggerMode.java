package com.foundation.sync.api.dto;

/** 同步触发方式（{@code fdn_sync_task.trigger_mode}）。 */
public enum SyncTriggerMode {

    MANUAL,

    SCHEDULED,

    EVENT;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncTriggerMode of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncTriggerMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
