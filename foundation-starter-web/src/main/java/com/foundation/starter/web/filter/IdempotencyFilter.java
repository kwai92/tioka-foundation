package com.foundation.starter.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.core.error.ApiResponse;
import com.foundation.starter.web.config.FoundationWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 幂等过滤器（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>仅处理写方法（POST/PUT/PATCH/DELETE）且携带非空 {@code Idempotency-Key}（trim 后 1..128）的请求。
 * 首次放行；重复请求直接以 409 + {@link ApiResponse} 拒绝，不再进入过滤链。若响应非 2xx（失败），
 * 在 {@code finally} 中释放键，保证客户端可重试。不使用 {@code synchronized}（48-CS §4）。
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    /** 幂等键请求头名。 */
    public static final String HEADER = "Idempotency-Key";

    private static final int MAX_KEY_LENGTH = 128;

    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

    private final IdempotencyStore store;
    private final FoundationWebProperties properties;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyStore store, FoundationWebProperties properties, ObjectMapper objectMapper) {
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return resolveKey(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = resolveKey(request);
        if (key == null || !store.markIfAbsent(key, properties.getIdempotencyTtl())) {
            writeConflict(response);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            if (status < HttpStatus.OK.value() || status >= HttpStatus.MULTIPLE_CHOICES.value()) {
                store.release(key);
            }
        }
    }

    private String resolveKey(HttpServletRequest request) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return null;
        }
        String raw = request.getHeader(HEADER);
        if (raw == null) {
            return null;
        }
        String key = raw.trim();
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            return null;
        }
        return key;
    }

    private void writeConflict(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Object> body = ApiResponse.fail(ErrorCode.CONFLICT, "重复请求：Idempotency-Key 已处理");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
