package com.foundation.starter.mq.consumer;

import com.foundation.starter.mq.config.FoundationMqProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 进程内消费幂等存储（F-02 BAS-24 §foundation-starter-mq）：无外置存储时的默认实现。
 *
 * <p>键为 {@code consumer + '\u0000' + messageId}，值为首次登记时间（毫秒）；按
 * {@code foundation.mq.consumer.retention-hours} 惰性过期，超过
 * {@code foundation.mq.consumer.max-memory-entries} 时清理最旧的一批。全部使用
 * {@link ConcurrentHashMap} 的原子语义（{@code putIfAbsent}/{@code replace}），不使用 synchronized。
 */
public class InMemoryConsumedStore implements ConsumedStore {

    private static final char KEY_SEPARATOR = '\u0000';

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final long retentionMillis;
    private final int maxMemoryEntries;

    public InMemoryConsumedStore(FoundationMqProperties properties) {
        this(TimeUnit.HOURS.toMillis(properties.getConsumer().getRetentionHours()),
                properties.getConsumer().getMaxMemoryEntries());
    }

    /** 供测试注入毫秒级保留窗口。 */
    InMemoryConsumedStore(long retentionMillis, int maxMemoryEntries) {
        this.retentionMillis = retentionMillis;
        this.maxMemoryEntries = maxMemoryEntries;
    }

    @Override
    public boolean markConsumed(String consumer, String messageId) {
        long now = System.currentTimeMillis();
        String key = consumer + KEY_SEPARATOR + messageId;
        purgeExpired(now);
        if (seen.size() >= maxMemoryEntries) {
            purgeOldest();
        }
        Long previous = seen.putIfAbsent(key, now);
        if (previous == null) {
            return true;
        }
        if (now - previous >= retentionMillis) {
            return seen.replace(key, previous, now);
        }
        return false;
    }

    private void purgeExpired(long now) {
        seen.entrySet().removeIf(entry -> now - entry.getValue() >= retentionMillis);
    }

    private void purgeOldest() {
        int batch = Math.max(1, maxMemoryEntries / 10);
        List<String> oldest = seen.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(batch)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
        oldest.forEach(seen::remove);
    }
}
