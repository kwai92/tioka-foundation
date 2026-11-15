package com.foundation.starter.mq.consumer;

/**
 * 消费幂等存储（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>以 {@code (consumer, messageId)} 为幂等键记录首次消费。
 */
public interface ConsumedStore {

    /**
     * 登记一次消费。
     *
     * @return {@code true} 表示首次消费成功登记；{@code false} 表示重复消费
     */
    boolean markConsumed(String consumer, String messageId);
}
