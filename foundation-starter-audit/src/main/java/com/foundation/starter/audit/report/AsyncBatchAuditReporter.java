package com.foundation.starter.audit.report;

import com.foundation.starter.audit.spool.DiskSpool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步批量审计上报（F-07 §6）。
 *
 * <p>语义：
 * <ul>
 *   <li><b>有界队列</b>：{@code report} 非阻塞；队列满则直接落盘（磁盘兜底）；</li>
 *   <li><b>批量</b>：队列达到 {@code batch-size} 或 {@code flush-interval} 到即发；</li>
 *   <li><b>重试</b>：指数退避重试，超过 {@code retry.max-attempts} 后转 {@link DiskSpool}；</li>
 *   <li><b>补偿</b>：后台线程扫描 spool 重放并清理。</li>
 * </ul>
 */
public class AsyncBatchAuditReporter implements AuditReporter, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncBatchAuditReporter.class);

    private final AuditTransport transport;
    private final DiskSpool spool;
    private final int batchSize;
    private final Duration flushInterval;
    private final int maxAttempts;
    private final Duration retryBaseDelay;
    private final BlockingQueue<AuditEvent> queue;

    private final AtomicInteger spooledCount = new AtomicInteger();
    private final AtomicInteger deliveredCount = new AtomicInteger();
    private final AtomicInteger replayedCount = new AtomicInteger();

    private ScheduledExecutorService scheduler;

    public AsyncBatchAuditReporter(AuditTransport transport,
                                   DiskSpool spool,
                                   int batchSize,
                                   Duration flushInterval,
                                   int maxAttempts,
                                   Duration retryBaseDelay,
                                   int queueCapacity) {
        this.transport = transport;
        this.spool = spool;
        this.batchSize = Math.max(1, batchSize);
        this.flushInterval = flushInterval;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseDelay = retryBaseDelay;
        this.queue = new LinkedBlockingQueue<>(Math.max(this.batchSize, queueCapacity));
    }

    @Override
    public void report(AuditEvent event) {
        if (event == null) {
            return;
        }
        if (!queue.offer(event)) {
            // 队列满：直接落盘，不阻塞业务
            spool.spoolAll(List.of(event));
            spooledCount.incrementAndGet();
            return;
        }
        if (queue.size() >= batchSize) {
            flush();
        }
    }

    /** 立即刷新（批量或间隔到即调用）；返回本次投递成功条数。 */
    public int flush() {
        List<AuditEvent> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) {
            return 0;
        }
        if (deliver(batch)) {
            deliveredCount.addAndGet(batch.size());
            return batch.size();
        }
        spool.spoolAll(batch);
        spooledCount.addAndGet(batch.size());
        return 0;
    }

    /** 扫描磁盘 spool 重放并清理；返回重放条数。 */
    public int replay() {
        List<Path> files = spool.pending();
        int replayed = 0;
        for (int start = 0; start < files.size(); start += batchSize) {
            List<Path> slice = files.subList(start, Math.min(start + batchSize, files.size()));
            List<AuditEvent> events = new ArrayList<>(slice.size());
            for (Path path : slice) {
                try {
                    events.add(spool.read(path));
                } catch (RuntimeException ex) {
                    log.warn("审计 spool 文件损坏，已清理：{}", path, ex);
                    spool.remove(path);
                }
            }
            if (events.isEmpty()) {
                continue;
            }
            if (!deliver(events)) {
                // 仍不可达：保留剩余文件，下轮再试
                break;
            }
            slice.forEach(spool::remove);
            replayed += events.size();
        }
        replayedCount.addAndGet(replayed);
        return replayed;
    }

    /** 启动后台刷新与补偿线程（由自动装配的 {@code initMethod} 调用）。 */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fdn-audit-reporter");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(1L, flushInterval.toMillis());
        scheduler.scheduleWithFixedDelay(this::flushAndReplay, interval, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        flush();
    }

    /** 当前队列积压条数。 */
    public int pendingCount() {
        return queue.size();
    }

    /** 累计落盘条数。 */
    public int spooledCount() {
        return spooledCount.get();
    }

    /** 累计投递条数。 */
    public int deliveredCount() {
        return deliveredCount.get();
    }

    /** 累计重放条数。 */
    public int replayedCount() {
        return replayedCount.get();
    }

    private void flushAndReplay() {
        try {
            flush();
            replay();
        } catch (RuntimeException ex) {
            log.warn("审计后台刷新失败", ex);
        }
    }

    private boolean deliver(List<AuditEvent> batch) {
        long delayMillis = Math.max(1L, retryBaseDelay.toMillis());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                transport.send(batch);
                return true;
            } catch (RuntimeException ex) {
                if (attempt == maxAttempts) {
                    log.warn("审计投递重试耗尽（{} 次），转入磁盘兜底：{}", maxAttempts, ex.getMessage());
                    return false;
                }
                sleep(delayMillis);
                delayMillis *= 2;
            }
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
