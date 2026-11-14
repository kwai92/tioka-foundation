package com.foundation.calendar.api.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作日历域事件体（F-09 BAS-18 §18.1）：只带 ID、版本与变化类型。
 *
 * <p>订阅方需要详情时回查接口（S15 §14.2）。工作日历无行版本列，{@code version} 恒为 {@code 0}。
 *
 * @param id         被变更日历主键（{@code cal_…}）
 * @param version    行版本（日历无版本列，固定 {@code 0}）
 * @param changeType 变化类型
 */
public record CalendarEventPayload(String id, long version, String changeType) {

    /** outbox 事件体（字段顺序稳定，便于幂等比对与测试）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("version", version);
        payload.put("changeType", changeType);
        return payload;
    }
}
