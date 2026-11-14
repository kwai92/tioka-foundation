package com.foundation.sync.api.dto;

/** 同步来源状态（{@code fdn_sync_source.status}）。 */
public enum SyncSourceStatus {

    ENABLED,

    DISABLED;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncSourceStatus of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncSourceStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
