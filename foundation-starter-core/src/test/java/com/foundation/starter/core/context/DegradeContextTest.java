package com.foundation.starter.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1：降级标记跨异步线程传递、回写与清理（不串请求）。 */
class DegradeContextTest {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() {
        DegradeContext.clear();
        pool.shutdownNow();
    }

    @Test
    void 主线程降级标记可被子线程继承() throws Exception {
        DegradeContext.mark();
        DegradeContext.Snapshot snapshot = DegradeContext.capture();

        assertTrue(pool.submit(() -> DegradeContext.call(snapshot, DegradeContext::isMarked)).get());
    }

    @Test
    void 子线程降级标记回写给父请求() throws Exception {
        DegradeContext.Snapshot snapshot = DegradeContext.capture();
        assertFalse(DegradeContext.isMarked());

        Future<?> future = pool.submit(DegradeContext.wrap(snapshot, DegradeContext::mark));
        future.get();

        assertTrue(DegradeContext.isMarked(), "异步任务里的降级必须能被外层过滤器读到");
        assertTrue(snapshot.isDegraded());
    }

    @Test
    void 异步任务结束后子线程上下文被清理() throws Exception {
        DegradeContext.Snapshot snapshot = DegradeContext.capture();

        assertTrue(pool.submit(() -> DegradeContext.call(snapshot, () -> {
            DegradeContext.mark();
            return DegradeContext.isMarked();
        })).get());
        assertFalse(pool.submit(DegradeContext::isMarked).get(),
                "线程池复用不得把上一次请求的降级标记带到下一次");
    }

    @Test
    void 任务抛异常时仍然清理上下文() throws Exception {
        DegradeContext.Snapshot snapshot = DegradeContext.capture();

        assertThrows(IllegalStateException.class,
                () -> DegradeContext.call(snapshot, () -> {
                    DegradeContext.mark();
                    throw new IllegalStateException("boom");
                }));
        assertFalse(DegradeContext.isMarked(), "call 的 finally 必须清理当前线程");
        assertFalse(pool.submit(DegradeContext::isMarked).get());
    }

    @Test
    void 不同请求的快照互不影响() {
        DegradeContext.Snapshot first = DegradeContext.capture();
        DegradeContext.mark();
        assertTrue(first.isDegraded());

        DegradeContext.clear();
        DegradeContext.Snapshot second = DegradeContext.capture();

        assertFalse(second.isDegraded(), "新请求不得继承上一次的降级状态");
        assertTrue(first.isDegraded(), "旧快照对象自身状态不被清理动作改写");
    }

    @Test
    void restore空快照等于清理当前线程() {
        DegradeContext.mark();
        DegradeContext.restore(null);

        assertFalse(DegradeContext.isMarked());
    }
}
