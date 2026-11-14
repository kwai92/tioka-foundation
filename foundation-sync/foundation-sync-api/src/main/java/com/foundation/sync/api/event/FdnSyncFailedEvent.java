package com.foundation.sync.api.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code fdn.sync.failed} 事件契约（F-08 BAS-11 §10）：同步失败。
 */
public final class FdnSyncFailedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.sync.failed";

    private FdnSyncFailedEvent() {
    }

    /**
     * 事件体。
     *
     * @param taskId    任务 ID
     * @param sourceCode 来源编码
     * @param errorCode 错误码（如 {@code INTERNAL} / {@code BAD_REQUEST}）
     */
    public static Map<String, Object> payload(String taskId, String sourceCode, String errorCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("sourceCode", sourceCode);
        payload.put("errorCode", errorCode);
        return payload;
    }
}
