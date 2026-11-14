package com.foundation.sync.api.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code fdn.sync.completed} 事件契约（F-08 BAS-11 §10）：同步（含对账）成功或部分成功。
 *
 * <p>经 {@code fdn_outbox} → 主题 {@code fdn-events} 投递；事件体只带任务标识与计数。
 */
public final class FdnSyncCompletedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.sync.completed";

    private FdnSyncCompletedEvent() {
    }

    /**
     * 事件体。
     *
     * @param taskId            任务 ID
     * @param sourceCode        来源编码
     * @param counts            计数（{@code created}/{@code updated}/{@code disabled}/{@code moved}）
     * @param capabilityMissing 是否含「能力缺失」标注
     */
    public static Map<String, Object> payload(String taskId, String sourceCode,
                                              Map<String, Integer> counts, boolean capabilityMissing) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("sourceCode", sourceCode);
        payload.put("counts", new LinkedHashMap<>(counts));
        payload.put("capabilityMissing", capabilityMissing);
        return payload;
    }
}
