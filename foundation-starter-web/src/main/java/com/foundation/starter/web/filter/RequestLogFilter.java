package com.foundation.starter.web.filter;

import com.foundation.starter.core.context.RequestContext;
import com.foundation.starter.web.config.FoundationWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求访问日志过滤器（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>记录 {@code method path status durationMs traceId}，并附脱敏后的 query 与 header；path 不含
 * query、不记录请求体。4xx/5xx 记 warn，其余记 info。
 */
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private final FoundationWebProperties properties;

    public RequestLogFilter(FoundationWebProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            int status = response.getStatus();
            String query = SensitiveDataMasker.maskQueryString(request.getQueryString(), properties.getSensitiveKeys());
            Map<String, String> headers = SensitiveDataMasker.maskHeaders(collectHeaders(request),
                    properties.getSensitiveKeys());
            Object[] args = {request.getMethod(), request.getRequestURI(), status, durationMs, traceId(), query, headers};
            if (status >= 400) {
                log.warn("http method={} path={} status={} durationMs={} traceId={} query={} headers={}", args);
            } else {
                log.info("http method={} path={} status={} durationMs={} traceId={} query={} headers={}", args);
            }
        }
    }

    private Map<String, List<String>> collectHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    private String traceId() {
        return RequestContext.traceId().orElse("-");
    }
}
