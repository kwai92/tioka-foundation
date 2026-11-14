package com.foundation.account.api.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号域事件体（F-04 §7）：只带 ID、版本与变化类型。
 *
 * <p>经 outbox → RocketMQ 主题 {@code fdn-events} 投递；订阅方需要详情时回查接口。
 *
 * @param id         被变更账号 ID（{@code acc_…}）
 * @param version    行版本（每次写 +1）
 * @param changeType 变化类型
 */
public record AccountEventPayload(String id, long version, String changeType) {

    /** outbox 事件体（字段顺序稳定，便于幂等比对与测试）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("version", version);
        payload.put("changeType", changeType);
        return payload;
    }
}
