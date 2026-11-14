package com.foundation.notify.infrastructure;

import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import com.foundation.notify.api.AnnouncementPublishedEvent;
import com.foundation.notify.domain.Announcement;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code fdn.announcement.published} 事件发布（F-09 BAS-16 §事件）。
 *
 * <p>经 outbox（{@code fdn_outbox}）→ 主题 {@code fdn-events} 投递；门户据此刷新消息与未读。
 */
public class AnnouncementEventPublisher {

    private final OutboxAppender outbox;
    private final EventProperties events;

    public AnnouncementEventPublisher(OutboxAppender outbox, EventProperties events) {
        this.outbox = outbox;
        this.events = events;
    }

    /** 发布公告发布事件。 */
    public void publish(Announcement announcement, int receiverCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", Ids.newId(CommonIdPrefix.OUTBOX_EVENT));
        payload.put("type", AnnouncementPublishedEvent.EVENT_TYPE);
        payload.put("announcementId", announcement.id());
        payload.put("scopeType", announcement.scopeType().name());
        payload.put("scopeId", announcement.scopeId());
        payload.put("receiverCount", receiverCount);
        payload.put("occurredAt", Instant.now().toString());
        outbox.append(events.getTopic(), AnnouncementPublishedEvent.EVENT_TYPE, announcement.id(), payload);
    }
}
