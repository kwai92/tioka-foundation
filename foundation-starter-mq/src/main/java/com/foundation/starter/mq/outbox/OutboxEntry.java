package com.foundation.starter.mq.outbox;

import java.time.LocalDateTime;

/**
 * outbox 行视图（F-02 BAS-24 §foundation-starter-mq）：{@code fdn_outbox} 的读模型。
 *
 * <p>{@code payload} 为 JSON 文本（DB 列为 {@code jsonb}）；时间列为 UTC 语义的
 * {@link LocalDateTime}（00-施工总纲 §2.2）。
 */
public record OutboxEntry(String id, String topic, String eventType, String aggregate,
                          String payload, String status, int retryCount,
                          LocalDateTime createAt, LocalDateTime sentAt) {

    /** 待投递。 */
    public static final String STATUS_NEW = "NEW";
    /** 已投递。 */
    public static final String STATUS_SENT = "SENT";
    /** 死信（超过重试上限）。 */
    public static final String STATUS_DEAD = "DEAD";
}
