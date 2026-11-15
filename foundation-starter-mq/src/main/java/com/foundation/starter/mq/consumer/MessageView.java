package com.foundation.starter.mq.consumer;

import com.foundation.common.id.Ids;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 消费侧消息视图（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>时间语义为 UTC 的 {@link LocalDateTime}（00-施工总纲 §2.2）。
 */
public record MessageView(String messageId, String topic, String tag, String body, LocalDateTime receivedAt) {

    /** 便捷构造：messageId 为空时用 {@code msg_{ulid}} 兜底，接收时间取当前 UTC。 */
    public static MessageView of(String messageId, String topic, String tag, String body) {
        String id = messageId == null || messageId.isBlank() ? Ids.newId("msg") : messageId;
        return new MessageView(id, topic, tag, body, LocalDateTime.now(Clock.systemUTC()));
    }
}
