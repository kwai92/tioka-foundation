package com.foundation.starter.cache.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单 JVM 固定窗口限流器（F-02 BAS-24 §foundation-starter-cache，{@code backend=local}）。
 *
 * <p>每个键维护「窗口结束时间 + 原子计数」；{@link ConcurrentHashMap#compute} 保证跨窗口切换的原子性，
 * 窗口内用 {@link AtomicInteger#incrementAndGet()} 计数。不使用 {@code synchronized}。
 */
public class LocalRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int permits, Duration window) {
        long windowMillis = Math.max(1L, window.toMillis());
        long now = System.currentTimeMillis();
        Window current = windows.compute(key, (k, existing) ->
                (existing == null || now >= existing.windowEnd())
                        ? new Window(now + windowMillis)
                        : existing);
        return current.count().incrementAndGet() <= permits;
    }

    /** 固定窗口：结束时间与计数。 */
    private static final class Window {

        private final long windowEnd;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long windowEnd) {
            this.windowEnd = windowEnd;
        }

        long windowEnd() {
            return windowEnd;
        }

        AtomicInteger count() {
            return count;
        }
    }
}
