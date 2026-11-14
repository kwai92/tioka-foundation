package com.foundation.notify.api;

import java.time.Instant;
import java.util.List;

/**
 * 消息发送完成事件（F-09 BAS-16 §事件 {@code fdn.message.sent}）。
 *
 * <p>经 outbox → RocketMQ 主题 {@code fdn-events} 投递；事件体只带 ID、接收人数与渠道，
 * 不带消息正文等敏感字段（订阅方需要详情时回查消息接口）。
 *
 * @param eventId      事件 ID（{@code evt_…}）
 * @param messageId    消息 ID
 * @param appCode      发送方应用编码
 * @param kind         消息类别名称
 * @param receiverCount 接收人数
 * @param channels     实际投递渠道
 * @param occurredAt   发生时间
 */
public record MessageSentEvent(String eventId,
                               String messageId,
                               String appCode,
                               String kind,
                               int receiverCount,
                               List<String> channels,
                               Instant occurredAt) {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.message.sent";
}
