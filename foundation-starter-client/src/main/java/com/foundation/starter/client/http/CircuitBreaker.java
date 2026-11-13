package com.foundation.starter.client.http;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 轻量熔断器（F-07 §11）：短窗口失败率超阈值即熔断，熔断期内直接用缓存 / 进入降级。
 *
 * <p>**不新增第三方依赖**：重试与熔断用 starter 自带的轻量实现，避免扩大 Foundation 依赖面
 * （{@code foundation-dependencies} 未列 Resilience4j）。
 */
public class CircuitBreaker {

    private final int failureRateThreshold;
    private final int minCalls;
    private final long windowMillis;
    private final long openMillis;
    private final Clock clock;

    private final Deque<Outcome> window = new ArrayDeque<>();
    private long openedAtMillis = -1;

    public CircuitBreaker(int failureRateThreshold, int minCalls, long windowMillis, long openMillis) {
        this(failureRateThreshold, minCalls, windowMillis, openMillis, Clock.systemUTC());
    }

    public CircuitBreaker(int failureRateThreshold, int minCalls, long windowMillis, long openMillis, Clock clock) {
        this.failureRateThreshold = failureRateThreshold;
        this.minCalls = Math.max(1, minCalls);
        this.windowMillis = windowMillis;
        this.openMillis = openMillis;
        this.clock = clock;
    }

    /** 是否放行本次调用。熔断开启期返回 {@code false}；开启期满进入半开放行。 */
    public synchronized boolean allow() {
        if (openedAtMillis < 0) {
            return true;
        }
        if (clock.millis() - openedAtMillis >= openMillis) {
            // 半开：放行一次探测
            openedAtMillis = -1;
            window.clear();
            return true;
        }
        return false;
    }

    /** 记录成功。 */
    public synchronized void recordSuccess() {
        window.addLast(new Outcome(clock.millis(), true));
        prune();
        openedAtMillis = -1;
    }

    /** 记录失败；短窗口失败率达到阈值即熔断。 */
    public synchronized void recordFailure() {
        window.addLast(new Outcome(clock.millis(), false));
        prune();
        if (window.size() >= minCalls && failureRatePercent() >= failureRateThreshold) {
            openedAtMillis = clock.millis();
        }
    }

    /** 当前是否处于熔断（开启）状态。 */
    public synchronized boolean isOpen() {
        return openedAtMillis >= 0 && clock.millis() - openedAtMillis < openMillis;
    }

    /** 短窗口失败率（百分比）。 */
    public synchronized int failureRatePercent() {
        prune();
        if (window.isEmpty()) {
            return 0;
        }
        long failures = window.stream().filter(outcome -> !outcome.success()).count();
        return (int) (failures * 100 / window.size());
    }

    private void prune() {
        long threshold = clock.millis() - windowMillis;
        while (!window.isEmpty() && window.peekFirst().atMillis() < threshold) {
            window.removeFirst();
        }
    }

    private record Outcome(long atMillis, boolean success) {
    }
}
