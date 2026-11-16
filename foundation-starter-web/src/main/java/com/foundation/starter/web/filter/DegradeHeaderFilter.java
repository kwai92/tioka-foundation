package com.foundation.starter.web.filter;

import com.foundation.starter.core.context.DegradeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把 SDK 的降级标记透传为响应头（36 号规划 ADP-08 / §6「降级语义」）。
 *
 * <p>注册在最外层，包住整个业务链；请求结束后读取 {@link DegradeContext} 并写
 * {@code X-Foundation-Degraded: true}，随后清理 ThreadLocal，避免线程池复用串标记。
 * 若响应体已经提交（流式下载等），响应头无法再写，此时以 WARN 日志与 SDK 侧审计为准。
 */
public class DegradeHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DegradeHeaderFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                if (DegradeContext.isMarked()) {
                    if (!response.isCommitted()) {
                        response.setHeader(DegradeContext.HEADER, "true");
                    } else {
                        log.warn("本次请求发生降级但响应已提交，无法透传 {}：{} {}",
                                DegradeContext.HEADER, request.getMethod(), request.getRequestURI());
                    }
                }
            } finally {
                DegradeContext.clear();
            }
        }
    }
}
