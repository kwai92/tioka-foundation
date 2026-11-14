package com.foundation.sync.api.dto;

/** 同步模式（{@code fdn_sync_task.mode}，F-08 BAS-11 §4）。 */
public enum SyncMode {

    /** 全量快照。 */
    FULL,

    /** 增量拉取。 */
    INCREMENTAL,

    /** 每日全量对账（修复漂移）。 */
    RECONCILE,

    /** 实时推送（SCIM，BAS-13）。 */
    PUSH;

    /** 是否为全量口径（决定是否计算「已消失对象 → 停用」差异）。 */
    public boolean isFullSnapshot() {
        return this == FULL || this == RECONCILE;
    }

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncMode of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
