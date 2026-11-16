package com.foundation.starter.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 按事件契约幂等的消费者基类（P1）：{@code eventType + deliveryId} 去重。
 *
 * <p>普通 {@link IdempotentConsumer} 用 MQ {@code messageId} 去重，只适合「一次投递一条消息」
 * 的场景；outbox 中继失败重试会重新发送并产生新 messageId，此时必须按平台保留字段
 * {@code deliveryId} 去重。新接入方直接继承本类即可，存量消费者行为不变。
 */
public abstract class DeliveryAwareIdempotentConsumer extends IdempotentConsumer {

    private final ObjectMapper objectMapper;

    protected DeliveryAwareIdempotentConsumer(ConsumedStore consumedStore, ObjectMapper objectMapper) {
        super(consumedStore);
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper is required");
        }
        this.objectMapper = objectMapper;
    }

    @Override
    protected String idempotencyKey(MessageView message) {
        return EventDeliveryKeys.idempotencyKey(message, objectMapper);
    }
}
