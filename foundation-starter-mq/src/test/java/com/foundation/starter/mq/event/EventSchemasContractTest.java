package com.foundation.starter.mq.event;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADP-01 / ADP-02：事件契约随构件发布且不可静默漂移。
 *
 * <p>删除事件类型、降低版本、改 envelope 字段都会让本测试变红，从而阻止破坏性变更合入。
 */
class EventSchemasContractTest {

    private static final Pattern EVENT_TYPE = Pattern.compile("^fdn\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_.]*$");

    @Test
    void 契约文件随starter发布() {
        try (InputStream in = EventSchemas.class.getResourceAsStream(EventSchemas.RESOURCE)) {
            assertNotNull(in, "契约文件必须在 jar 的 classpath 上：" + EventSchemas.RESOURCE);
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
        assertEquals(1, EventSchemas.currentSchemaVersion());
    }

    @Test
    void 关键事件已登记且名称合法() {
        Set<String> known = EventSchemas.knownEventTypes();
        assertTrue(known.size() >= 20, "契约事件清单不应缩水");
        for (String eventType : known) {
            assertTrue(EVENT_TYPE.matcher(eventType).matches(), "非法事件名：" + eventType);
            assertEquals(1, EventSchemas.versionOf(eventType), "v1 契约版本应一致：" + eventType);
        }
        for (String required : new String[]{"fdn.authz.changed", "fdn.person.disabled",
                "fdn.dict.changed", "fdn.param.changed", "fdn.sync.completed"}) {
            assertTrue(EventSchemas.isKnown(required), "关键事件被删除：" + required);
        }
    }

    @Test
    void 未登记事件回落到缺省版本_保持前向兼容() {
        assertEquals(DomainEvent.DEFAULT_SCHEMA_VERSION, EventSchemas.versionOf("fdn.future.added"));
    }

    @Test
    void envelope必需字段冻结() {
        assertEquals(Set.of("schemaVersion", "eventType", "aggregate", "deliveryId", "payload"),
                EventSchemas.envelopeRequiredFields());
    }
}
