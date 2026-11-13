package com.foundation.starter.security.principal;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * 请求线程内的当前人员（F-07 §2）。
 *
 * <p>用 {@link ThreadLocal} 承载；由请求边界（过滤器或 {@link CurrentUserArgumentResolver}）
 * 写入并在结束后 {@link #clear()}。后台任务（定时、MQ 消费）无请求线程，必须先
 * {@link #runAs} 恢复身份后才可调 {@link #require()}。
 */
public final class PrincipalHolder {

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    private PrincipalHolder() {
    }

    /** 取当前人员；未登录返回空。 */
    public static Optional<Principal> current() {
        Holder holder = CURRENT.get();
        return holder == null ? Optional.empty() : Optional.ofNullable(holder.principal());
    }

    /** 取当前人员；未登录抛 {@code UNAUTHORIZED(40100)} → HTTP 401。 */
    public static Principal require() {
        return current().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    /** 取当前会话生效的快照版本号；未设置返回空。 */
    public static OptionalLong snapshotVersion() {
        Holder holder = CURRENT.get();
        return holder == null || holder.snapshotVersion() < 0
                ? OptionalLong.empty()
                : OptionalLong.of(holder.snapshotVersion());
    }

    /** 以给定身份与快照版本绑定当前线程（请求边界调用）。 */
    public static void set(Principal principal, long snapshotVersion) {
        Holder holder = new Holder();
        holder.principal(principal);
        holder.snapshotVersion(snapshotVersion);
        CURRENT.set(holder);
    }

    /**
     * 后台任务恢复身份：以快照指定版本运行，避免长任务的权限漂移（F-07 §2）。
     *
     * @param principal       恢复的人员身份，可为空（仅恢复版本）
     * @param snapshotVersion 绑定的快照版本号
     * @param action          在线程内执行的动作
     */
    public static <T> T runAs(Principal principal, long snapshotVersion, Supplier<T> action) {
        Holder previous = CURRENT.get();
        try {
            set(principal, snapshotVersion);
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** 清空当前身份（请求边界调用，避免线程复用串号）。 */
    public static void clear() {
        CURRENT.remove();
    }

    private static final class Holder {

        private Principal principal;
        private long snapshotVersion = -1;

        Principal principal() {
            return principal;
        }

        void principal(Principal value) {
            this.principal = value;
        }

        long snapshotVersion() {
            return snapshotVersion;
        }

        void snapshotVersion(long value) {
            this.snapshotVersion = value;
        }
    }
}
