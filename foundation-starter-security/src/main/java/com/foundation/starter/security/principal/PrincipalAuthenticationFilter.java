package com.foundation.starter.security.principal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 请求边界身份注入（F-07 §2）。
 *
 * <p>{@link PrincipalHolder} 用 {@link ThreadLocal} 承载当前身份，必须由请求边界写入、并在请求结束后清空。
 * 本过滤器即该写入方：
 * <ol>
 *   <li>用 {@link PrincipalResolver} 解析令牌（无令牌 / 无效令牌一律返回空，不抛异常）；</li>
 *   <li>解析出身份时绑定到当前线程；</li>
 *   <li>请求结束（含异常）在 {@code finally} 中 {@link PrincipalHolder#clear()}，避免容器线程复用串号。</li>
 * </ol>
 *
 * <p>未解析出身份时既不写入也不拦截：是否拒绝由 {@code @RequirePermission} 的
 * {@code PermissionAspect} 判定（401），{@code @PublicApi} 与
 * {@code foundation.security.public-api-paths} 直接放行。
 *
 * <p>此处不绑定快照版本：请求线程内由 {@code PermissionAspect} 按 {@code personId} 取快照并校验，
 * 快照版本绑定只服务于后台任务的 {@link PrincipalHolder#runAs}。
 */
public class PrincipalAuthenticationFilter extends OncePerRequestFilter {

    private final PrincipalResolver principalResolver;

    public PrincipalAuthenticationFilter(PrincipalResolver principalResolver) {
        this.principalResolver = principalResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Optional<Principal> principal = principalResolver.resolve(request);
            principal.ifPresent(value -> PrincipalHolder.set(value, -1));
            chain.doFilter(request, response);
        } finally {
            PrincipalHolder.clear();
        }
    }
}
