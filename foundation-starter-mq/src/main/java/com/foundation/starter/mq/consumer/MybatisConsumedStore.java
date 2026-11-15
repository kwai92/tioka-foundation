package com.foundation.starter.mq.consumer;

/**
 * 基于 MyBatis-Plus 的消费幂等存储（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>{@code insertConsumed} 依赖 {@code ON CONFLICT DO NOTHING}：影响行数 &gt; 0 判定首次，
 * 并发安全（48-CS §4：ORM 仅 MyBatis-Plus）。
 */
public class MybatisConsumedStore implements ConsumedStore {

    private final ConsumedMessageMapper consumedMessageMapper;

    public MybatisConsumedStore(ConsumedMessageMapper consumedMessageMapper) {
        this.consumedMessageMapper = consumedMessageMapper;
    }

    @Override
    public boolean markConsumed(String consumer, String messageId) {
        return consumedMessageMapper.insertConsumed(consumer, messageId) > 0;
    }
}
