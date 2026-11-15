package com.foundation.starter.mq.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link DomainEvent} 校验（F-02 BAS-24 §foundation-starter-mq）。 */
class DomainEventTest {

    @Test
    void 合法事件名通过() {
        DomainEvent event = DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_1", Map.of("id", "dt_1"));

        assertEquals("fdn.dict.changed", event.eventType());
        assertEquals("dt_1", event.aggregate());
        assertEquals(1, event.payload().size());
    }

    @Test
    void 非法事件名抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainEvent.of("fdn-events", "dict.changed", "dt_1", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DomainEvent.of("fdn-events", "fdn.Dict.changed", "dt_1", Map.of()));
    }

    @Test
    void 空白字段抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainEvent.of("  ", "fdn.dict.changed", "dt_1", Map.of()));
    }

    @Test
    void payload为空变空Map() {
        DomainEvent event = DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_1", null);

        assertTrue(event.payload().isEmpty());
    }

    @Test
    void 四参数工厂保持缺省契约版本_向后兼容() {
        DomainEvent event = DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_1", Map.of("id", "dt_1"));

        assertEquals(DomainEvent.DEFAULT_SCHEMA_VERSION, event.schemaVersion());
    }

    @Test
    void 可显式指定契约版本() {
        DomainEvent event = DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_1",
                Map.of("id", "dt_1"), 2);

        assertEquals(2, event.schemaVersion());
    }

    @Test
    void 非法契约版本拒绝() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainEvent.of("fdn-events", "fdn.dict.changed", "dt_1", Map.of(), 0));
    }
}
