package com.foundation.sync.domain;

import com.foundation.sync.api.dto.SyncPauseReason;

/**
 * 删除保护判定结果（F-08 BAS-11 §6）。
 *
 * @param pause  是否需暂停待确认
 * @param reason 暂停原因（{@code pause=TRUE} 时为 {@link SyncPauseReason#THRESHOLD}）
 */
public record DeleteProtectionVerdict(boolean pause, SyncPauseReason reason) {

    /** 未超阈值，照常落地。 */
    public static DeleteProtectionVerdict pass() {
        return new DeleteProtectionVerdict(false, null);
    }

    /** 超阈值，暂停落地剩余停用。 */
    public static DeleteProtectionVerdict paused() {
        return new DeleteProtectionVerdict(true, SyncPauseReason.THRESHOLD);
    }
}
