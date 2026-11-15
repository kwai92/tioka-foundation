package com.foundation.starter.mq.relay;

import com.foundation.starter.mq.config.FoundationMqProperties;
import com.foundation.starter.mq.event.DomainEvent;
import com.foundation.starter.mq.mq.MqTemplate;
import com.foundation.starter.mq.outbox.Outbox;
import com.foundation.starter.mq.outbox.OutboxEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link OutboxRelay#pollOnce()} 行为（F-02 BAS-24 §foundation-starter-mq）。 */
class OutboxRelayTest {

    @Test
    void 正常投递后标记已发送() {
        FakeOutbox outbox = new FakeOutbox(List.of(entry("evt_1", "{\"n\":1}"), entry("evt_2", "{\"n\":2}")));
        RecordingMqTemplate mq = new RecordingMqTemplate(Set.of());
        OutboxRelay relay = new OutboxRelay(outbox, mq, properties(100));

        int sent = relay.pollOnce();

        assertEquals(2, sent);
        assertEquals(List.of("evt_1", "evt_2"), outbox.sent);
        assertTrue(outbox.retried.isEmpty());
        assertEquals(2, mq.destinations.size());
    }

    @Test
    void 单条失败不中断整批且走重试() {
        FakeOutbox outbox = new FakeOutbox(List.of(
                entry("evt_1", "{\"n\":1}"),
                entry("evt_2", "{\"n\":2}"),
                entry("evt_3", "{\"n\":3}")));
        RecordingMqTemplate mq = new RecordingMqTemplate(Set.of("{\"n\":2}"));
        OutboxRelay relay = new OutboxRelay(outbox, mq, properties(100));

        int sent = relay.pollOnce();

        assertEquals(2, sent);
        assertEquals(List.of("evt_1", "evt_3"), outbox.sent);
        assertEquals(List.of("evt_2:5"), outbox.retried);
    }

    @Test
    void 返回成功投递条数() {
        FakeOutbox outbox = new FakeOutbox(List.of(entry("evt_1", "{\"n\":1}")));
        OutboxRelay relay = new OutboxRelay(outbox, new RecordingMqTemplate(Set.of()), properties(100));

        assertEquals(1, relay.pollOnce());
    }

    @Test
    void 并列通道成功后才标记已发送() {
        FakeOutbox outbox = new FakeOutbox(List.of(entry("evt_1", "{\"n\":1}")));
        List<String> delivered = new ArrayList<>();
        EventSink sink = new EventSink() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public void deliver(OutboxEntry entry) {
                delivered.add(entry.id());
            }
        };
        OutboxRelay relay = new OutboxRelay(outbox, new RecordingMqTemplate(Set.of()),
                properties(100), null, List.of(sink));

        assertEquals(1, relay.pollOnce());
        assertEquals(List.of("evt_1"), delivered);
        assertEquals(List.of("evt_1"), outbox.sent);
    }

    @Test
    void 并列通道失败时整条转重试() {
        FakeOutbox outbox = new FakeOutbox(List.of(entry("evt_1", "{\"n\":1}")));
        EventSink failing = new EventSink() {
            @Override
            public String name() {
                return "failing";
            }

            @Override
            public void deliver(OutboxEntry entry) {
                throw new IllegalStateException("模拟 Webhook 5xx");
            }
        };
        OutboxRelay relay = new OutboxRelay(outbox, new RecordingMqTemplate(Set.of()),
                properties(100), null, List.of(failing));

        assertEquals(0, relay.pollOnce());
        assertTrue(outbox.sent.isEmpty(), "任一通道失败时不能标记已发送");
        assertEquals(List.of("evt_1:5"), outbox.retried);
    }

    private static FoundationMqProperties properties(int batchSize) {
        FoundationMqProperties properties = new FoundationMqProperties();
        properties.getRelay().setBatchSize(batchSize);
        return properties;
    }

    private static OutboxEntry entry(String id, String body) {
        return new OutboxEntry(id, "fdn-events", "fdn.dict.changed", "dt_1", body,
                OutboxEntry.STATUS_NEW, 0, LocalDateTime.now(), null);
    }

    /** 测试替身：内存 outbox。 */
    private static final class FakeOutbox implements Outbox {

        private final List<OutboxEntry> pending;
        private final List<String> sent = new ArrayList<>();
        private final List<String> retried = new ArrayList<>();

        FakeOutbox(List<OutboxEntry> pending) {
            this.pending = pending;
        }

        @Override
        public void append(DomainEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<OutboxEntry> fetchPending(int limit) {
            return pending;
        }

        @Override
        public void markSent(String id) {
            sent.add(id);
        }

        @Override
        public void markRetryOrDead(String id, int maxRetry) {
            retried.add(id + ":" + maxRetry);
        }
    }

    /** 测试替身：记录型 MqTemplate，body 命中 failBodies 时抛异常。 */
    private static final class RecordingMqTemplate implements MqTemplate {

        private final Set<String> failBodies;
        private final List<String> destinations = new ArrayList<>();

        RecordingMqTemplate(Set<String> failBodies) {
            this.failBodies = failBodies;
        }

        @Override
        public void send(String topic, String tag, String body) {
            if (failBodies.contains(body)) {
                throw new IllegalStateException("模拟投递失败: " + topic);
            }
            destinations.add(topic + ":" + tag);
        }
    }
}
