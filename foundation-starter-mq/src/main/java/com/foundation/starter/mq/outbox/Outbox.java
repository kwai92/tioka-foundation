package com.foundation.starter.mq.outbox;

import com.foundation.starter.mq.event.DomainEvent;

import java.util.List;

/**
 * outbox 存储与状态流转（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>{@link #append(DomainEvent)} 必须在业务事务内调用，保证「业务落库」与「事件落库」同生共死
 * （S15 §14.2）；投递由 {@code OutboxRelay} 在事务外异步完成。
 */
public interface Outbox {

    /** 追加一条待投递事件（要求调用方处于已有事务中）。 */
    void append(DomainEvent event);

    /** 取一批待投递事件（按 {@code create_at} 升序）。 */
    List<OutboxEntry> fetchPending(int limit);

    /** 标记已投递。 */
    void markSent(String id);

    /**
     * 标记一次投递失败：{@code retry_count + 1 >= maxRetry} 置 {@code DEAD}，
     * 否则重置为 {@code NEW} 且 {@code retry_count} 递增，同时清空 {@code sent_at}。
     */
    void markRetryOrDead(String id, int maxRetry);
}
