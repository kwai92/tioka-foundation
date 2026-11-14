package com.foundation.sync.api.event;

import com.foundation.sync.api.dto.SyncPauseReason;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code fdn.sync.paused} 事件契约（F-08 BAS-11 §10）：差异超阈值暂停待确认。
 */
public final class FdnSyncPausedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.sync.paused";

    private FdnSyncPausedEvent() {
    }

    /**
     * 事件体。
     *
     * @param taskId      任务 ID
     * @param sourceCode  来源编码
     * @param pauseReason 暂停原因
     * @param counts      计数
     */
    public static Map<String, Object> payload(String taskId, String sourceCode,
                                              SyncPauseReason pauseReason, Map<String, Integer> counts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("sourceCode", sourceCode);
        payload.put("pauseReason", pauseReason == null ? null : pauseReason.name());
        payload.put("counts", new LinkedHashMap<>(counts));
        return payload;
    }
}
