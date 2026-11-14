package com.foundation.notify.infrastructure;

import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import com.foundation.notify.api.MessageSentEvent;
import com.foundation.notify.domain.Message;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code fdn.message.sent} 事件发布（F-09 BAS-16 §事件）。
 *
 * <p>经 outbox（{@code fdn_outbox}）→ 主题 {@code fdn-events} 投递；事件体只带 ID、接收人数与渠道，
 * 不带消息正文等敏感字段（S15 §14.2），与业务落库同事务。
 */
public class MessageEventPublisher {

    private final OutboxAppender outbox;
    private final EventProperties events;

    public MessageEventPublisher(OutboxAppender outbox, EventProperties events) {
        this.outbox = outbox;
        this.events = events;
    }

    /** 发布消息发送完成事件。 */
    public void publishSent(Message message, List<String> channels, int receiverCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", Ids.newId(CommonIdPrefix.OUTBOX_EVENT));
        payload.put("type", MessageSentEvent.EVENT_TYPE);
        payload.put("messageId", message.id());
        payload.put("appCode", message.appCode());
        payload.put("kind", message.kind().name());
        payload.put("receiverCount", receiverCount);
        payload.put("channels", channels);
        payload.put("occurredAt", Instant.now().toString());
        outbox.append(events.getTopic(), MessageSentEvent.EVENT_TYPE, message.id(), payload);
    }
}
