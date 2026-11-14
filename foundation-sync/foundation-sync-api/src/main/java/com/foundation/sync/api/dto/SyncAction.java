package com.foundation.sync.api.dto;

/** 同步日志动作（{@code fdn_sync_log.action}）。 */
public enum SyncAction {

    CREATE,

    UPDATE,

    DISABLE,

    MOVE,

    /** 跳过（本地锁定字段、能力缺失、非权威来源越界等）。 */
    SKIP,

    ERROR;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncAction of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncAction action : values()) {
            if (action.name().equalsIgnoreCase(value.trim())) {
                return action;
            }
        }
        return null;
    }
}
