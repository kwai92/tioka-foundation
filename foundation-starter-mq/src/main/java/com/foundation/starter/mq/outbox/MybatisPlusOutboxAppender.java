package com.foundation.starter.mq.outbox;

import com.foundation.common.api.event.OutboxAppender;
import com.foundation.starter.mq.event.DomainEvent;

import java.util.Map;

/**
 * {@link OutboxAppender} 的 MyBatis-Plus 适配（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>各领域 {@code -biz} 原有的 {@code JdbcOutboxAppender} 已由本类取代（48-CS §4：ORM 仅 MyBatis-Plus）；
 * 公共域装配（{@code CommonAutoConfiguration}）以本类型暴露全上下文唯一的 {@link OutboxAppender} Bean。
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
