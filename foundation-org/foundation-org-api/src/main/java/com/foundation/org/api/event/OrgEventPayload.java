package com.foundation.org.api.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组织域事件体（F-03 §6）：只带 ID、版本与变化类型。
 *
 * <p>四个事件（{@code fdn.org.changed}、{@code fdn.person.created}/{@code updated}/{@code disabled}）
 * 共用本结构；订阅方需要详情时回查接口，敏感字段（手机、邮箱）不进消息。
 *
 * @param id         被变更对象主键（{@code org_…} / {@code per_…}）
 * @param version    行版本（每次写 +1），订阅方据此判重与排序
 * @param changeType 变化类型
 */
public record OrgEventPayload(String id, long version, String changeType) {

    /** outbox 事件体（字段顺序稳定，便于幂等比对与测试）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("version", version);
        payload.put("changeType", changeType);
        return payload;
    }
}
