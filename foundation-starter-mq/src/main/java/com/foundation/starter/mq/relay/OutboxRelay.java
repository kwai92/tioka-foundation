package com.foundation.starter.mq.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.mq.config.FoundationMqProperties;
import com.foundation.starter.mq.mq.MqTemplate;
import com.foundation.starter.mq.outbox.Outbox;
import com.foundation.starter.mq.outbox.OutboxEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * outbox 中继（F-02 BAS-24 §foundation-starter-mq）：把 {@code fdn_outbox} 的待投递事件搬到 MQ。
 *
 * <p>不依赖 Spring 调度（便于单测）：{@link #start()} 在 {@code relay.enabled=true} 时用单线程
 * daemon {@link ScheduledExecutorService} 按 {@code relay.interval} 调 {@link #pollOnce()}；
 * {@link #stop()} 中 {@code shutdownNow}。单条失败不中断整批，失败条目走重试/死信。
 * 不使用 synchronized / ReentrantLock（48-CS §4）。
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String THREAD_NAME = "fdn-outbox-relay";

    private final Outbox outbox;
    private final MqTemplate mqTemplate;
    private final FoundationMqProperties properties;
    /** 保留以对齐签名；中继只原样透传 {@link OutboxEntry#payload()} 字符串。 */
    private final ObjectMapper objectMapper;
    /** 并列投递通道（ADP-02）：MQ 之后依次调用，任一失败整条转重试/死信。 */
    private final List<EventSink> eventSinks;

    private volatile ScheduledExecutorService executor;

    /** 构造器：不注入 ObjectMapper（{@code null}），无附加通道。 */
    public OutboxRelay(Outbox outbox, MqTemplate mqTemplate, FoundationMqProperties properties) {
        this(outbox, mqTemplate, properties, null, List.of());
    }

    public OutboxRelay(Outbox outbox, MqTemplate mqTemplate, FoundationMqProperties properties,
                       ObjectMapper objectMapper) {
        this(outbox, mqTemplate, properties, objectMapper, List.of());
    }

    /** 带并列通道的构造器；由自动装配注入所有 {@link EventSink} Bean。 */
    public OutboxRelay(Outbox outbox, MqTemplate mqTemplate, FoundationMqProperties properties,
                       ObjectMapper objectMapper, List<EventSink> eventSinks) {
        this.outbox = outbox;
        this.mqTemplate = mqTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventSinks = eventSinks == null ? List.of() : List.copyOf(eventSinks);
    }

    /**
     * 轮询一次：拉一批待投递事件逐条投递。
     *
     * <p>成功 {@code markSent}；单条异常 {@code markRetryOrDead} 并 {@code log.warn}，不影响其余条目。
     *
     * @return 成功投递条数
     */
    public int pollOnce() {
        List<OutboxEntry> pending = outbox.fetchPending(properties.getRelay().getBatchSize());
        int sent = 0;
        for (OutboxEntry entry : pending) {
            try {
                mqTemplate.send(entry.topic(), entry.eventType(), entry.payload());
                for (EventSink sink : eventSinks) {
                    sink.deliver(entry);
                }
                outbox.markSent(entry.id());
                sent++;
            } catch (RuntimeException ex) {
                log.warn("outbox 投递失败，转重试/死信: id={}, eventType={}", entry.id(), entry.eventType(), ex);
                outbox.markRetryOrDead(entry.id(), properties.getRelay().getMaxRetry());
            }
        }
        return sent;
    }

    /** 按 {@code relay.interval} 周期调用 {@link #pollOnce()}（{@code relay.enabled=false} 时不启动）。 */
    public void start() {
        if (!properties.getRelay().isEnabled() || executor != null) {
            return;
        }
        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = properties.getRelay().getInterval().toMillis();
        scheduled.scheduleWithFixedDelay(this::pollQuietly, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        this.executor = scheduled;
    }

    /** 停止调度。 */
    public void stop() {
        ScheduledExecutorService scheduled = this.executor;
        this.executor = null;
        if (scheduled != null) {
            scheduled.shutdownNow();
        }
    }

    private void pollQuietly() {
        try {
            pollOnce();
        } catch (RuntimeException ex) {
            log.warn("outbox 中继轮询失败", ex);
        }
    }
}
