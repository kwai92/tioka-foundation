package com.foundation.sync.api.dto;

/** 同步冲突类型（{@code fdn_sync_conflict.conflict_type}，F-08 BAS-11 §8）。 */
public enum SyncConflictType {

    /** 主键（幂等键）重复。 */
    DUPLICATE_KEY,

    /** 被本地锁定字段跳过覆盖。 */
    LOCKED_FIELD,

    /** 非权威来源越界写主数据。 */
    AUTHORITY_CONFLICT,

    /** 外部身份候选关联不唯一。 */
    IDENTITY_AMBIGUOUS;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static SyncConflictType of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncConflictType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
