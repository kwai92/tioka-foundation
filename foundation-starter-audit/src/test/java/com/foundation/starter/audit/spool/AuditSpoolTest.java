package com.foundation.starter.audit.spool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.foundation.starter.audit.report.AsyncBatchAuditReporter;
import com.foundation.starter.audit.report.AuditEvent;
import com.foundation.starter.audit.report.AuditTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** 审计磁盘兜底与批量上报（F-07 §6、§15）：批量触发、重试耗尽落盘、补偿线程重放并清理。 */
class AuditSpoolTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @TempDir
    Path spoolDir;

    @Test
    void spoolWritesReadsAndRemoves() {
        DiskSpool spool = newSpool();

        Path file = spool.spool(event("aud_1"));

        assertThat(spool.size()).isEqualTo(1);
        assertThat(spool.read(file).eventId()).isEqualTo("aud_1");

        spool.remove(file);

        assertThat(spool.size()).isZero();
        assertThat(spool.pending()).isEmpty();
    }

    @Test
    void flushTriggeredWhenBatchSizeReached() {
        RecordingTransport transport = new RecordingTransport();
        AsyncBatchAuditReporter reporter = reporter(transport, newSpool(), 3, 2, 10);

        reporter.report(event("aud_1"));
        reporter.report(event("aud_2"));

        assertThat(transport.batches()).isEmpty();

        reporter.report(event("aud_3"));

        assertThat(transport.sentIds()).containsExactly("aud_1", "aud_2", "aud_3");
        assertThat(reporter.deliveredCount()).isEqualTo(3);
        assertThat(reporter.spooledCount()).isZero();
        assertThat(reporter.pendingCount()).isZero();
    }

    @Test
    void closeFlushesPendingEvents() {
        RecordingTransport transport = new RecordingTransport();
        AsyncBatchAuditReporter reporter = reporter(transport, newSpool(), 100, 2, 100);

        reporter.report(event("aud_1"));
        assertThat(transport.sentIds()).isEmpty();

        reporter.close();

        assertThat(transport.sentIds()).containsExactly("aud_1");
    }

    @Test
    void retryExhaustedSpoolsToDisk() {
        DiskSpool spool = newSpool();
        AsyncBatchAuditReporter reporter = reporter(new FailingTransport(), spool, 1, 2, 10);

        reporter.report(event("aud_1"));

        assertThat(reporter.spooledCount()).isEqualTo(1);
        assertThat(reporter.deliveredCount()).isZero();
        assertThat(spool.size()).isEqualTo(1);
        assertThat(spool.read(spool.pending().get(0)).eventId()).isEqualTo("aud_1");
    }

    @Test
    void replayDeliversAndClearsSpool() {
        DiskSpool spool = newSpool();
        spool.spoolAll(List.of(event("aud_1"), event("aud_2")));
        RecordingTransport transport = new RecordingTransport();
        AsyncBatchAuditReporter reporter = reporter(transport, spool, 10, 2, 10);

        int replayed = reporter.replay();

        assertThat(replayed).isEqualTo(2);
        assertThat(reporter.replayedCount()).isEqualTo(2);
        assertThat(transport.sentIds()).containsExactlyInAnyOrder("aud_1", "aud_2");
        assertThat(spool.size()).isZero();
    }

    @Test
    void replayKeepsSpoolWhenTransportStillDown() {
        DiskSpool spool = newSpool();
        spool.spoolAll(List.of(event("aud_1")));
        AsyncBatchAuditReporter reporter = reporter(new FailingTransport(), spool, 10, 1, 10);

        assertThat(reporter.replay()).isZero();
        assertThat(spool.size()).isEqualTo(1);
    }

    private DiskSpool newSpool() {
        return new DiskSpool(spoolDir, MAPPER);
    }

    private static AsyncBatchAuditReporter reporter(AuditTransport transport, DiskSpool spool,
                                                    int batchSize, int maxAttempts, int queueCapacity) {
        return new AsyncBatchAuditReporter(transport, spool, batchSize,
                Duration.ofSeconds(30), maxAttempts, Duration.ofMillis(1), queueCapacity);
    }

    private static AuditEvent event(String eventId) {
        return new AuditEvent(eventId, Instant.parse("2026-01-01T00:00:00Z"), "tioka",
                "user.update", "user:1", AuditEvent.RESULT_SUCCESS,
                "1", "per_1", "trace-1", null, 12L);
    }

    /** 记录投递内容的传输桩。 */
    private static final class RecordingTransport implements AuditTransport {

        private final List<AuditEvent> sent = new CopyOnWriteArrayList<>();
        private final List<List<AuditEvent>> batches = new CopyOnWriteArrayList<>();

        @Override
        public void send(List<AuditEvent> batch) {
            batches.add(List.copyOf(batch));
            sent.addAll(batch);
        }

        List<List<AuditEvent>> batches() {
            return batches;
        }

        List<String> sentIds() {
            return sent.stream().map(AuditEvent::eventId).toList();
        }
    }

    /** 始终失败的传输桩（模拟 Foundation 不可达）。 */
    private static final class FailingTransport implements AuditTransport {

        @Override
        public void send(List<AuditEvent> batch) {
            throw new IllegalStateException("foundation down");
        }
    }
}
