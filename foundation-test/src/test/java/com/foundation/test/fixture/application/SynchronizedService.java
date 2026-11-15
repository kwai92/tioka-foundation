package com.foundation.test.fixture.application;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 规则 8 反例：application 层用 synchronized 方法与 ReentrantLock 字段做业务互斥
 * （仅用于演示规则，真实代码应改用 DB/Redis 锁）。
 */
public class SynchronizedService {

    private final ReentrantLock lock = new ReentrantLock();

    public synchronized void run() {
        lock.lock();
        try {
            // 仅演示：真实代码禁止这样做业务互斥
        } finally {
            lock.unlock();
        }
    }
}
