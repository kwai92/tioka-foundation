package com.foundation.authz.infrastructure;

import com.foundation.authz.api.AuthzChangedEvent;
import com.foundation.authz.domain.AuthzSnapshot;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code fdn.authz.changed} 事件发布（F-06 BAS-07 §失效与事件）。
 *
 * <p>经 outbox（{@code fdn_outbox}）→ 主题 {@code fdn-events} 投递；事件体只带 ID、版本与
 * 变化类型，不带敏感字段。要求授权变更后 5 秒内发出、{@code snapshotVersion} 单调递增。
 * 与业务落库同事务（S15 §14.2）。
 */
public class AuthzChangedEventPublisher {

    private final OutboxAppender outbox;
    private final EventProperties events;

    public AuthzChangedEventPublisher(OutboxAppender outbox, EventProperties events) {
        this.outbox = outbox;
        this.events = events;
    }

    /** 发布授权重算事件。 */
    public void publish(AuthzSnapshot snapshot) {
        publish(snapshot, AuthzChangedEvent.CHANGE_TYPE_RECOMPUTED);
    }

    /** 发布指定变化类型的授权变更事件。 */
    public void publish(AuthzSnapshot snapshot, String changeType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", Ids.newId(CommonIdPrefix.OUTBOX_EVENT));
        payload.put("type", AuthzChangedEvent.EVENT_TYPE);
        payload.put("personId", snapshot.personId());
        payload.put("appCode", snapshot.appCode());
        payload.put("snapshotVersion", snapshot.version());
        payload.put("changeType", changeType);
        payload.put("occurredAt", snapshot.computedAt().toString());
        outbox.append(events.getTopic(), AuthzChangedEvent.EVENT_TYPE, snapshot.personId(), payload);
    }
}
