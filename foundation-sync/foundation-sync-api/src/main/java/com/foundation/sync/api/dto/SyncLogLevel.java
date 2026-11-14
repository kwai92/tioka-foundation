package com.foundation.sync.api.dto;

/** 同步日志级别（{@code fdn_sync_log.level}）。 */
public enum SyncLogLevel {

    INFO,

    WARN,

    ERROR;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncLogLevel of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncLogLevel level : values()) {
            if (level.name().equalsIgnoreCase(value.trim())) {
                return level;
            }
        }
        return null;
    }
}
