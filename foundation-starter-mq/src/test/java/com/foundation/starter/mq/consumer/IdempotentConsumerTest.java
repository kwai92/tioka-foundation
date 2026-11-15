package com.foundation.starter.mq.consumer;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link IdempotentConsumer} 行为（F-02 BAS-24 §foundation-starter-mq）。 */
class IdempotentConsumerTest {

    @Test
    void 重复消息只处理一次() {
        CountingConsumer consumer = new CountingConsumer(new SetConsumedStore());
        MessageView message = MessageView.of("m1", "fdn-events", "fdn.dict.changed", "{}");

        assertTrue(consumer.consume(message));
        assertFalse(consumer.consume(message));
        assertEquals(1, consumer.count.get());
    }

    @Test
    void handle异常时向上抛出且不吞() {
        IdempotentConsumer consumer = new IdempotentConsumer(new SetConsumedStore()) {
            @Override
            protected String consumerName() {
                return "c";
            }

            @Override
            protected void handle(MessageView message) {
                throw new IllegalStateException("boom");
            }
        };
        MessageView message = MessageView.of("m2", "fdn-events", "fdn.dict.changed", "{}");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> consumer.consume(message));
        assertEquals("boom", ex.getMessage());
    }

    /** 计数消费器。 */
    private static final class CountingConsumer extends IdempotentConsumer {

        private final AtomicInteger count = new AtomicInteger();

        CountingConsumer(ConsumedStore consumedStore) {
            super(consumedStore);
        }

        @Override
        protected String consumerName() {
            return "counting";
        }

        @Override
        protected void handle(MessageView message) {
            count.incrementAndGet();
        }
    }

    /** 测试替身：集合型幂等存储。 */
    private static final class SetConsumedStore implements ConsumedStore {

        private final Set<String> keys = ConcurrentHashMap.newKeySet();

        @Override
        public boolean markConsumed(String consumer, String messageId) {
            return keys.add(consumer + '\u0000' + messageId);
        }
    }
}
