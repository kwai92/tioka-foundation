package com.foundation.starter.cache.lock;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式锁（F-02 BAS-24 §foundation-starter-cache）。
 *
 * <p>业务互斥一律走本接口：单实例退化为进程内实现，多实例切 Redisson 实现；
 * 禁止在业务代码里使用 {@code synchronized} / {@code ReentrantLock}。
 */
public interface DistributedLock {

    /** {@link #withLock} 的默认租约：与 {@code foundation.cache.lock-lease} 缺省值一致。 */
    Duration DEFAULT_LOCK_LEASE = Duration.ofSeconds(30);

    /**
     * 尝试加锁：在 {@code wait} 内轮询等待，成功则在 {@code lease} 后自动过期。
     *
     * @return 是否拿到锁；等待超时返回 {@code false}，不抛异常
     */
    boolean tryLock(String key, Duration wait, Duration lease);

    /** 释放锁（仅释放由当前持有者持有的锁；未持有时为无操作）。 */
    void unlock(String key);

    /**
     * 持锁执行并在结束后释放；拿不到锁抛 {@link ErrorCode#CONFLICT}。
     */
    default <T> T withLock(String key, Duration wait, Supplier<T> action) {
        if (!tryLock(key, wait, DEFAULT_LOCK_LEASE)) {
            throw new BizException(ErrorCode.CONFLICT, "获取分布式锁失败：" + key);
        }
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }
}
