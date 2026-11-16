package com.foundation.starter.mq.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 幂等消费者基类（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>先经 {@link ConsumedStore} 去重：重复消息直接跳过；首次消息调 {@link #handle(MessageView)}，
 * 处理异常时 {@code log.warn} 并向上抛出（不吞异常），由上层决定重试/死信。
 */
public abstract class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final ConsumedStore consumedStore;

    protected IdempotentConsumer(ConsumedStore consumedStore) {
        this.consumedStore = consumedStore;
    }

    /** 消费者名（幂等键之一，落 {@code fdn_consumed.consumer}）。 */
    protected abstract String consumerName();

    /** 处理首次消息。 */
    protected abstract void handle(MessageView message);

    /**
     * 幂等键（P1）。默认沿用 MQ {@code messageId}，保持既有消费者零改动。
     *
     * <p>注意：outbox 中继重试会产生<b>新的</b> messageId，因此按事件契约
     * （{@code eventType + deliveryId}）去重的消费者应覆盖本方法，或直接使用
     * {@link DeliveryAwareIdempotentConsumer}。{@code deliveryId} 是 P1 起写入
     * MQ body 的平台保留字段，与 outbox 行主键同源、重试不变。
     */
    protected String idempotencyKey(MessageView message) {
        return message.messageId();
    }

    /**
     * 消费一条消息。
     *
     * @return {@code true} 表示首次消费并已处理；{@code false} 表示重复消息已跳过
     */
    public final boolean consume(MessageView message) {
        String key = idempotencyKey(message);
        boolean first = consumedStore.markConsumed(consumerName(), key);
        if (!first) {
            log.debug("重复消息已跳过: consumer={}, idempotencyKey={}", consumerName(), key);
            return false;
        }
        try {
            handle(message);
        } catch (RuntimeException ex) {
            log.warn("消息处理失败: consumer={}, idempotencyKey={}", consumerName(), key, ex);
            throw ex;
        }
        return true;
    }
}
