package com.foundation.account.infrastructure;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * 每日对账单实例锁（F-04 §6；S15 §4「同一时间一个实例在跑」）。
 *
 * <p>基于 Redis {@code SET NX EX} 实现：获取成功即持有锁，到期自动释放，避免实例崩溃后死锁。
 */
public class AccountReconcileLock {

    /** 锁键（全局单实例）。 */
    static final String LOCK_KEY = "fdn:account:reconcile:lock";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public AccountReconcileLock(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    /** 尝试获取锁；成功返回令牌，失败返回 {@code null}。 */
    public String tryLock() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /** 释放锁：仅当持有者令牌一致时删除，避免误删他人锁。 */
    public void unlock(String token) {
        if (token == null) {
            return;
        }
        String current = redis.opsForValue().get(LOCK_KEY);
        if (token.equals(current)) {
            redis.delete(LOCK_KEY);
        }
    }
}
