package com.foundation.starter.mq.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import com.foundation.starter.mq.event.DomainEvent;

import java.util.List;

/**
 * 基于 MyBatis-Plus 的 outbox 实现（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>{@link #append(DomainEvent)} 必须在业务事务内调用（与业务落库同生共死，S15 §14.2）；
 * {@code ObjectMapper} 由外部注入，禁止在本类内 new。
 */
public class MybatisOutbox implements Outbox {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public MybatisOutbox(OutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(DomainEvent event) {
        OutboxDO row = new OutboxDO();
        row.setId(Ids.newId(CommonIdPrefix.OUTBOX_EVENT));
        row.setTopic(event.topic());
        row.setEventType(event.eventType());
        row.setAggregate(event.aggregate());
        // ADP-02：契约版本随事件体落库（新增字段，对既有订阅方兼容）。
        // 用 LinkedHashMap 保持业务字段顺序，便于排查与快照比对。
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>(event.payload());
        body.putIfAbsent("schemaVersion", event.schemaVersion());
        row.setPayload(toJson(body));
        outboxMapper.insertOutbox(row);
    }

    @Override
    public List<OutboxEntry> fetchPending(int limit) {
        return outboxMapper.selectPending(limit).stream().map(this::toEntry).toList();
    }

    @Override
    public void markSent(String id) {
        outboxMapper.markSent(id);
    }

    @Override
    public void markRetryOrDead(String id, int maxRetry) {
        OutboxDO row = outboxMapper.selectById(id);
        int retry = row == null || row.getRetryCount() == null ? 0 : row.getRetryCount();
        boolean dead = retry + 1 >= maxRetry;
        outboxMapper.markRetry(id, dead);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("事件 payload 序列化失败", ex);
        }
    }

    private OutboxEntry toEntry(OutboxDO row) {
        int retry = row.getRetryCount() == null ? 0 : row.getRetryCount();
        return new OutboxEntry(row.getId(), row.getTopic(), row.getEventType(), row.getAggregate(),
                row.getPayload(), row.getStatus(), retry, row.getCreateAt(), row.getSentAt());
    }
}
