package com.foundation.sync.spi;

import java.time.Instant;

/**
 * 增量游标（F-08 BAS-11 §2、§7）：增量成功后由引擎持久化到 {@code fdn_sync_task.cursor_json}。
 *
 * @param mode      增量方式
 * @param value     游标值（时间戳文本 / 变更号），首轮为空表示从头拉取
 * @param timestamp 游标时间戳（{@link IncrementalMode#TIMESTAMP} 语义），可空
 * @param deleteCursor 删除标记游标（事件回调场景），可空
 */
public record IncrementalCursor(IncrementalMode mode, String value, Instant timestamp, boolean deleteCursor) {

    /** 初始游标（无值 = 从头拉取）。 */
    public static IncrementalCursor initial(IncrementalMode mode) {
        return new IncrementalCursor(mode, null, null, false);
    }

    /** 以新值推进（由适配器在 {@code advanceCursor} 内计算）。 */
    public IncrementalCursor advanced(String newValue, Instant newTimestamp) {
        return new IncrementalCursor(mode, newValue, newTimestamp, deleteCursor);
    }

    /** 是否有可用游标值。 */
    public boolean hasValue() {
        return value != null && !value.isBlank();
    }
}
