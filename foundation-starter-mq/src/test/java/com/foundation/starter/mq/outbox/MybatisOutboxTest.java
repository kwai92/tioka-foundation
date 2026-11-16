package com.foundation.starter.mq.outbox;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.foundation.starter.mq.event.DomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link MybatisOutbox} 行为（F-02 BAS-24 §foundation-starter-mq）。 */
class MybatisOutboxTest {

    private final OutboxMapper outboxMapper = mock(OutboxMapper.class);
    private final MybatisOutbox outbox = new MybatisOutbox(outboxMapper, JsonMapper.builder().build());

    @Test
    void append生成evt前缀与JSON载荷() {
        outbox.append(DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_1", Map.of("id", "dt_1")));

        ArgumentCaptor<OutboxDO> captor = ArgumentCaptor.forClass(OutboxDO.class);
        verify(outboxMapper).insertOutbox(captor.capture());
        OutboxDO row = captor.getValue();
        assertTrue(row.getId().startsWith("evt_"), "主键前缀应为 evt_");
        assertEquals("fdn.dict.changed", row.getEventType());
        assertEquals("dt_1", row.getAggregate());
        assertEquals("{\"id\":\"dt_1\",\"schemaVersion\":1}", row.getPayload());
    }

    @Test
    void fetchPending映射为条目() {
        OutboxDO row = new OutboxDO();
        row.setId("evt_1");
        row.setTopic("fdn-events");
        row.setEventType("fdn.dict.changed");
        row.setAggregate("dt_1");
        row.setPayload("{\"id\":\"dt_1\"}");
        row.setStatus(OutboxEntry.STATUS_NEW);
        row.setRetryCount(0);
        when(outboxMapper.selectPending(10)).thenReturn(List.of(row));

        List<OutboxEntry> pending = outbox.fetchPending(10);

        assertEquals(1, pending.size());
        assertEquals("evt_1", pending.get(0).id());
        assertEquals(OutboxEntry.STATUS_NEW, pending.get(0).status());
    }

    @Test
    void 重试超限时置死信() {
        OutboxDO row = new OutboxDO();
        row.setId("evt_x");
        row.setRetryCount(4);
        when(outboxMapper.selectById("evt_x")).thenReturn(row);

        outbox.markRetryOrDead("evt_x", 5);

        verify(outboxMapper).markRetry("evt_x", true);
    }

    @Test
    void 重试未超限时保持待投递() {
        OutboxDO row = new OutboxDO();
        row.setId("evt_y");
        row.setRetryCount(1);
        when(outboxMapper.selectById("evt_y")).thenReturn(row);

        outbox.markRetryOrDead("evt_y", 5);

        verify(outboxMapper).markRetry("evt_y", false);
    }
}
