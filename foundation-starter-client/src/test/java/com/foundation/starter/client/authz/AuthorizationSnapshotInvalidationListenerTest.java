package com.foundation.starter.client.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.integration.SnapshotInvalidationHook;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1：fdn.authz.changed 事件到缓存失效的转换，以及非法事件不得被静默吞掉。 */
class AuthorizationSnapshotInvalidationListenerTest {

    private static final String APP = "tioka-ai";
    private static final String PERSON = "person-001";

    private final AtomicReference<String> invalidated = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final SnapshotInvalidationHook hook = personId -> {
        calls.incrementAndGet();
        invalidated.set(personId);
    };
    private final AuthorizationSnapshotInvalidationListener listener =
            new AuthorizationSnapshotInvalidationListener(APP, hook, new ObjectMapper());

    @Test
    void MQ事件体触发本应用缓存失效() {
        assertTrue(listener.onMessage(mqBody(PERSON, APP, "fdn.authz.changed")));
        assertEquals(PERSON, invalidated.get());
        assertEquals(1, calls.get());
    }

    @Test
    void Webhook信封的业务字段在payload里也能触发失效() {
        String envelope = "{\"schemaVersion\":1,\"eventType\":\"fdn.authz.changed\","
                + "\"aggregate\":\"" + PERSON + "\",\"deliveryId\":\"evt_1\",\"payload\":"
                + mqBody(PERSON, APP, "fdn.authz.changed") + "}";

        assertTrue(listener.onMessage(envelope));
        assertEquals(PERSON, invalidated.get());
    }

    @Test
    void 跨应用事件忽略_不误清本应用缓存() {
        assertFalse(listener.onMessage(mqBody(PERSON, "other-app", "fdn.authz.changed")));
        assertEquals(0, calls.get());
        assertNull(invalidated.get());
    }

    @Test
    void 非授权变更事件忽略() {
        assertFalse(listener.onMessage(mqBody(PERSON, APP, "fdn.dict.changed")));
        assertEquals(0, calls.get());
    }

    @Test
    void 缺少关键字段的非法事件必须抛出_不能静默吞() {
        assertThrows(IllegalStateException.class, () -> listener.onMessage(null));
        assertThrows(IllegalStateException.class, () -> listener.onMessage("  "));
        assertThrows(IllegalStateException.class, () -> listener.onMessage("{broken"));
        assertThrows(IllegalStateException.class, () -> listener.onMessage("[]"));
        assertThrows(IllegalStateException.class,
                () -> listener.onMessage("{\"type\":\"fdn.authz.changed\",\"appCode\":\"" + APP + "\"}"));
        assertThrows(IllegalStateException.class,
                () -> listener.onMessage("{\"type\":\"fdn.authz.changed\",\"personId\":\"" + PERSON + "\"}"));
        assertEquals(0, calls.get(), "非法事件不得被当成失效成功");
    }

    private static String mqBody(String personId, String appCode, String eventType) {
        return "{\"eventId\":\"evt_1\",\"type\":\"" + eventType + "\",\"personId\":\"" + personId
                + "\",\"appCode\":\"" + appCode
                + "\",\"snapshotVersion\":8,\"changeType\":\"AUTHZ_RECOMPUTED\","
                + "\"occurredAt\":\"2026-11-16T00:00:00Z\"}";
    }
}
