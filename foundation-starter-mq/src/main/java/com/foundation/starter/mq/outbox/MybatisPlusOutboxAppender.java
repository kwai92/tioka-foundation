package com.foundation.starter.mq.outbox;

import com.foundation.common.api.event.OutboxAppender;
import com.foundation.starter.mq.event.DomainEvent;

import java.util.Map;

/**
 * {@link OutboxAppender} 的 MyBatis-Plus 适配（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>各领域 {@code -biz} 里现存的 {@code JdbcOutboxAppender} 可被本类替换（48-CS §4：ORM 仅 MyBatis-Plus）。
 */
public class MybatisPlusOutboxAppender implements OutboxAppender {

    private final Outbox outbox;

    public MybatisPlusOutboxAppender(Outbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public void append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
        outbox.append(DomainEvent.of(topic, eventType, aggregate, payload));
    }
}
