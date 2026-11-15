package com.foundation.starter.web.filter;

import java.time.Duration;

/**
 * 幂等键存储（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>业务互斥只允许数据库行锁或分布式锁（48-CS §4）；本接口为进程内缺省实现提供契约，
 * 生产环境可替换为 Redis 实现。
 */
public interface IdempotencyStore {

    /**
     * 键不存在（或已过期）时登记并返回 {@code true}；键仍存活时返回 {@code false}。
     *
     * @param key 幂等键
     * @param ttl 存活时长
     * @return true 表示首次登记
     */
    boolean markIfAbsent(String key, Duration ttl);

    /** 释放键（供失败重试）。 */
    void release(String key);
}
