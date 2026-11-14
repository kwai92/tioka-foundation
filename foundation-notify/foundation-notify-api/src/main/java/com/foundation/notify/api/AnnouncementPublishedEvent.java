package com.foundation.notify.api;

import java.time.Instant;

/**
 * 公告发布事件（F-09 BAS-16 §事件 {@code fdn.announcement.published}）。
 *
 * <p>经 outbox → RocketMQ 主题 {@code fdn-events} 投递；门户据此刷新消息与未读。
 *
 * @param eventId        事件 ID（{@code evt_…}）
 * @param announcementId 公告 ID
 * @param scopeType      范围类型名称
 * @param scopeId        范围目标 ID，可空
 * @param receiverCount  展开的收件人数
 * @param occurredAt     发生时间
 */
public record AnnouncementPublishedEvent(String eventId,
                                         String announcementId,
                                         String scopeType,
                                         String scopeId,
                                         int receiverCount,
                                         Instant occurredAt) {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.announcement.published";
}
