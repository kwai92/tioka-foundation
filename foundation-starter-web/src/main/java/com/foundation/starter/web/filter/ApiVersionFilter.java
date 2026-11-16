package com.foundation.starter.web.filter;

import com.foundation.starter.core.contract.FoundationApiVersion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 对外接口主版本协商（36 号规划 ADP-03）。
 *
 * <p>请求头 {@code X-Foundation-Api-Version} 缺省或为 v1 时放行；显式声明了服务端不支持的
 * 主版本返回 406 + 明确错误码，格式非法返回 400，绝不落到 500。响应一律回写当前主版本，
 * 便于客户端判断并行发布期内连接的还是哪一个版本的服务端。
 *
 * <p>只做主版本校验，不做路径分版（v1/v2 路径会破坏 ADP-01 冻结的接口清单基线）；
 * 并行期内由网关按版本头路由到不同实例，旧 starter 在并行期内零改动可用。
 */
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader(FoundationApiVersion.HEADER, FoundationApiVersion.CURRENT);
        String raw = request.getHeader(FoundationApiVersion.HEADER);
        if (raw != null && !raw.isBlank()) {
            Integer major = FoundationApiVersion.parseMajor(raw);
            if (major == null) {
                log.warn("非法 API 版本头：{} {} {}", request.getMethod(), request.getRequestURI(), raw);
                reject(response, HttpServletResponse.SC_BAD_REQUEST,
                        FoundationApiVersion.MALFORMED_CODE,
                        "X-Foundation-Api-Version 格式非法，应为 v1 或 1");
                return;
            }
            if (major != FoundationApiVersion.CURRENT_MAJOR) {
                log.warn("不支持的 API 主版本：{} {} v{}", request.getMethod(),
                        request.getRequestURI(), major);
                reject(response, HttpServletResponse.SC_NOT_ACCEPTABLE,
                        FoundationApiVersion.UNSUPPORTED_CODE,
                        "不支持的 API 主版本 v" + major + "，当前服务端仅支持 v"
                                + FoundationApiVersion.CURRENT_MAJOR);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"supported\":\"" + FoundationApiVersion.CURRENT + "\"}");
    }
}
