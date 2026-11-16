package com.foundation.starter.core.context;

/**
 * 降级标记的请求上下文（36 号规划 ADP-08 / §6「降级语义」）。
 *
 * <p>当只读能力因 Foundation 不可用而返回过期缓存时，SDK 在此打标记；Web 层过滤器把它
 * 透传为响应头 {@code X-Foundation-Degraded: true} 并在请求结束时清理。业务方据此判断
 * 数据新鲜度，日志中同时留下 WARN 审计记录。
 *
 * <p><b>身份与授权类能力不允许 fail-open</b>；授权快照只有在显式配置了
 * {@code foundation.authz.snapshot.fail-mode=stale} 时才会打标记，默认仍是拒绝（deny）。
 */
public final class DegradeContext {

    /** 降级标记响应头。 */
    public static final String HEADER = "X-Foundation-Degraded";

    private static final ThreadLocal<Boolean> DEGRADED = new ThreadLocal<>();

    private DegradeContext() {
    }

    /** 标记本次请求使用了降级数据。 */
    public static void mark() {
        DEGRADED.set(Boolean.TRUE);
    }

    /** 本次请求是否发生过降级。 */
    public static boolean isMarked() {
        return Boolean.TRUE.equals(DEGRADED.get());
    }

    /** 请求结束时清理，避免线程池复用导致标记串请求。 */
    public static void clear() {
        DEGRADED.remove();
    }
}
