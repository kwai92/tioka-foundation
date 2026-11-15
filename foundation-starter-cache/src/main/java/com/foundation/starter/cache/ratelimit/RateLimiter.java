package com.foundation.starter.cache.ratelimit;

import java.time.Duration;

/**
 * 限流器（F-02 BAS-24 §foundation-starter-cache）。
 *
 * <p>固定窗口计数；超限返回 {@code false}，由业务侧决定拒绝或降级，本接口不抛异常。
 */
public interface RateLimiter {

    /**
     * 尝试获取 {@code permits} 个许可。
     *
     * @param key     限流键（经 {@code KeyPrefix} 加前缀）
     * @param permits 窗口内允许的许可数
     * @param window  窗口长度
     * @return 是否获取成功
     */
    boolean tryAcquire(String key, int permits, Duration window);
}
