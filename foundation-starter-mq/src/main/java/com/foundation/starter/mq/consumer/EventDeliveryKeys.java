package com.foundation.starter.mq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * 从事件 body 解析平台保留字段 {@code deliveryId}（P1）。
 *
 * <p>outbox 落库时把行主键写入 payload 的 {@code deliveryId}，MQ / Webhook 两条通道
 * 因此共用同一个投递标识。消费方按 {@code eventType + deliveryId} 去重，
 * 中继重试（MQ 会换 messageId）就不会造成重复处理。
 *
 * <p>解析失败时返回 {@link Optional#empty()}，由调用方回退到 {@code messageId}，
 * <b>不吞事件</b>——业务处理仍会看到非法报文并自行报错。
 */
public final class EventDeliveryKeys {

    /** 平台保留字段名，与 {@code MybatisOutbox} / {@code WebhookEventSink} 同源。 */
    public static final String FIELD = "deliveryId";

    private EventDeliveryKeys() {
    }

    /** 读取 deliveryId；非法 JSON、缺字段或非文本值一律返回空。 */
    public static Optional<String> deliveryId(String body, ObjectMapper objectMapper) {
        if (body == null || body.isBlank() || objectMapper == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode value = root == null || !root.isObject() ? null : root.get(FIELD);
            return value != null && value.isTextual() && !value.asText().isBlank()
                    ? Optional.of(value.asText())
                    : Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 契约幂等键：{@code tag(eventType) + deliveryId}；无法解析时回退 MQ messageId。
     * tag 缺失也回退，避免把不同事件揉成同一个键。
     */
    public static String idempotencyKey(MessageView message, ObjectMapper objectMapper) {
        String eventType = message == null ? null : message.tag();
        if (eventType == null || eventType.isBlank()) {
            return message == null ? null : message.messageId();
        }
        return deliveryId(message.body(), objectMapper)
                .map(id -> eventType + ":" + id)
                .orElseGet(() -> message == null ? null : message.messageId());
    }
}
