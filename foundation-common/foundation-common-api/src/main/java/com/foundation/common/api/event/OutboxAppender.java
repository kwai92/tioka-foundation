package com.foundation.common.api.event;

import java.util.Map;

/**
 * outbox 追加器（与 BAS-24 {@code foundation-starter-mq} 的中继共用 {@code fdn_outbox} 约定）。
 *
 * <p>契约放 {@code -api}：各领域 {@code -biz} 只依赖对方的 {@code -api}（F-05 硬约束），
 * 实现（{@code MybatisPlusOutboxAppender}）由公共域装配提供。
 *
 * <p>必须在已有事务中调用，保证「业务落库」与「事件落库」同生共死（S15 §14.2）。
 */
public interface OutboxAppender {

    /**
     * 追加一条待投递事件。
     *
     * @param topic     投递主题（{@code foundation.event.topic}）
     * @param eventType 事件名，如 {@code fdn.dict.changed}
     * @param aggregate 聚合标识（被变更对象的主键）
     * @param payload   事件体；按 S15 §14.2 只带 ID、版本与变化类型
     */
    void append(String topic, String eventType, String aggregate, Map<String, Object> payload);
}
