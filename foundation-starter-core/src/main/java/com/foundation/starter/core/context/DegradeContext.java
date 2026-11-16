package com.foundation.starter.core.context;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 降级标记的请求上下文（36 号规划 ADP-08 / §6「降级语义」）。
 *
 * <p>当只读能力因 Foundation 不可用而返回过期缓存时，SDK 在此打标记；Web 层过滤器把它
 * 透传为响应头 {@code X-Foundation-Degraded: true} 并在请求结束时清理。业务方据此判断
 * 数据新鲜度，日志中同时留下 WARN 审计记录。
 *
 * <p><b>身份与授权类能力不允许 fail-open</b>；授权快照只有在显式配置了
 * {@code foundation.authz.snapshot.fail-mode=stale} 时才会打标记，默认仍是拒绝（deny）。
 *
 * <p><b>P1 异步传递</b>：纯 ThreadLocal 在 {@code @Async} / 线程池 / 消息回调里会丢标记，
 * 导致响应头不写。平台提供 {@link #capture()} → {@link #wrap(Snapshot, Runnable)} /
 * {@link #call(Snapshot, Supplier)} 的显式传播：同一 {@link Snapshot} 在父子线程间共享，
 * 子线程里 {@link #mark()} 会回写到父线程持有的快照，父线程过滤器仍能读到并透传。
 * 不调用 capture 的存量代码行为完全不变；{@link #clear()} 依旧按线程清理，不串请求。
 */
public final class DegradeContext {

    /** 降级标记响应头。 */
    public static final String HEADER = "X-Foundation-Degraded";

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private DegradeContext() {
    }

    /**
     * 一次请求的可共享降级状态。跨线程传递时父子持有同一实例，
     * 子线程的降级标记通过它回写给父线程；不可被业务方伪造已降级状态。
     */
    public static final class Snapshot {

        private final AtomicBoolean degraded;

        private Snapshot(boolean degraded) {
            this.degraded = new AtomicBoolean(degraded);
        }

        /** 该请求是否已发生过降级。 */
        public boolean isDegraded() {
            return degraded.get();
        }

        private void mark() {
            degraded.set(true);
        }
    }

    /** 标记本次请求使用了降级数据；同时回写到本线程绑定的共享快照。 */
    public static void mark() {
        State state = state();
        state.marked = true;
        if (state.shared != null) {
            state.shared.mark();
        }
    }

    /** 本次请求是否发生过降级（含子线程回写的标记）。 */
    public static boolean isMarked() {
        State state = STATE.get();
        return state != null
                && (state.marked || (state.shared != null && state.shared.isDegraded()));
    }

    /**
     * 取得当前请求的共享快照；没有则创建并绑定当前线程。
     * 过滤器在请求入口调用一次，业务提交异步任务时再次调用会拿到同一个实例。
     */
    public static Snapshot capture() {
        State state = state();
        if (state.shared == null) {
            state.shared = new Snapshot(state.marked);
        }
        return state.shared;
    }

    /** 在异步线程里恢复父请求上下文；{@code null} 等价于清理当前线程。 */
    public static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        State state = state();
        state.shared = snapshot;
        state.marked = snapshot.isDegraded();
    }

    /** 包装异步任务：进入时恢复上下文，退出时必定清理，避免线程池复用串标记。 */
    public static Runnable wrap(Snapshot snapshot, Runnable task) {
        Objects.requireNonNull(task, "task");
        return () -> {
            try {
                restore(snapshot);
                task.run();
            } finally {
                clear();
            }
        };
    }

    /** 包装有返回值的异步任务，语义同 {@link #wrap(Snapshot, Runnable)}。 */
    public static <T> T call(Snapshot snapshot, Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        try {
            restore(snapshot);
            return task.get();
        } finally {
            clear();
        }
    }

    /** 请求结束时清理，避免线程池复用导致标记串请求。 */
    public static void clear() {
        STATE.remove();
    }

    private static State state() {
        State state = STATE.get();
        if (state == null) {
            state = new State();
            STATE.set(state);
        }
        return state;
    }

    private static final class State {

        private boolean marked;
        private Snapshot shared;
    }
}
