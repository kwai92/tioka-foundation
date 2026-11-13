package com.foundation.starter.core.context;

import java.util.Optional;

/**
 * 请求上下文（F-02 BAS-24 §foundation-starter-core）：请求线程内承载 {@code traceId} 与 {@code appCode}。
 *
 * <p>出站调用透传 {@code traceparent} 与 {@code request_id}（M0-01 ENG-11 / GOV-08）；由请求边界
 * （过滤器或参数解析器）写入与清空，后台任务用 {@link #runWith} 显式恢复。
 */
public final class RequestContext {

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    /** 取当前 traceId。 */
    public static Optional<String> traceId() {
        Holder holder = CURRENT.get();
        return holder == null ? Optional.empty() : Optional.ofNullable(holder.traceId());
    }

    /** 取当前应用编码。 */
    public static Optional<String> appCode() {
        Holder holder = CURRENT.get();
        return holder == null ? Optional.empty() : Optional.ofNullable(holder.appCode());
    }

    /** 设置当前上下文的 traceId 与 appCode（缺省保留原值）。 */
    public static void set(String traceId, String appCode) {
        Holder holder = CURRENT.get();
        if (holder == null) {
            holder = new Holder();
            CURRENT.set(holder);
        }
        if (traceId != null && !traceId.isBlank()) {
            holder.traceId(traceId);
        }
        if (appCode != null && !appCode.isBlank()) {
            holder.appCode(appCode);
        }
    }

    /** 在给定上下文中执行，执行后恢复原上下文（供后台任务复用）。 */
    public static <T> T runWith(String traceId, String appCode, java.util.function.Supplier<T> action) {
        Holder previous = CURRENT.get();
        try {
            set(traceId, appCode);
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** 清空当前上下文（请求边界调用，避免线程复用串号）。 */
    public static void clear() {
        CURRENT.remove();
    }

    private static final class Holder {

        private String traceId;
        private String appCode;

        String traceId() {
            return traceId;
        }

        void traceId(String value) {
            this.traceId = value;
        }

        String appCode() {
            return appCode;
        }

        void appCode(String value) {
            this.appCode = value;
        }
    }
}
