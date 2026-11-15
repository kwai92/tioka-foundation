package com.foundation.starter.cache.ratelimit;

import com.foundation.starter.cache.key.KeyPrefix;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * Redisson 限流器（F-02 BAS-24 §foundation-starter-cache，{@code backend=redisson}）。
 *
 * <p>用 {@code trySetRate} 幂等地初始化窗口（已初始化时返回 {@code false} 且不覆盖），
 * 再用 {@code tryAcquire()} 取许可。键统一经 {@link KeyPrefix} 加前缀。
 */
public class RedissonRateLimiter implements RateLimiter {

    private final RedissonClient redisson;
    private final KeyPrefix keyPrefix;

    public RedissonRateLimiter(RedissonClient redisson, KeyPrefix keyPrefix) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean tryAcquire(String key, int permits, Duration window) {
        RRateLimiter limiter = redisson.getRateLimiter(keyPrefix.of(key));
        limiter.trySetRate(RateType.OVERALL, permits, Math.max(1L, window.toMillis()),
                RateIntervalUnit.MILLISECONDS);
        return limiter.tryAcquire();
    }
}
