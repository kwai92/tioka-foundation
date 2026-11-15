package com.foundation.starter.observability.trace;

import com.foundation.common.id.Ids;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.context.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * traceId 透传过滤器（F-02 BAS-24 §foundation-starter-observability）。
 *
 * <p>按 W3C Trace Context 与 Foundation 约定解析入站 trace 上下文，优先级：
 * <ol>
 *   <li>请求头 {@code traceparent}（格式 {@code 00-<32 位 hex traceId>-<16 位 hex spanId>-<2 位 hex flags>}，
 *       合法则复用其中 traceId）；</li>
 *   <li>请求头 {@code X-Request-Id}（非空且长度不超过 64 则复用）；</li>
 *   <li>都不满足时用 {@link Ids#newId} 生成。</li>
 * </ol>
 *
 * <p>写入 {@link RequestContext} 与 MDC，响应头回写 {@code X-Trace-Id}（以及合法 trace 上下文下的
 * {@code traceparent}），请求结束在 {@code finally} 中清理，避免线程复用串号。
 *
 * <p>仅在 Servlet web 应用中装配（类路径需有 spring-web），非 web 进程整体不激活。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** 入站 trace 上下文请求头（W3C Trace Context）。 */
    public static final String HEADER_TRACEPARENT = "traceparent";

    /** Foundation 约定的 traceId 请求/响应头。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 上游请求号请求头（无 traceparent 时复用）。 */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** MDC 键：日志格式以此为占位（对齐 GOV-08 统一日志字段）。 */
    public static final String MDC_TRACE_ID = "traceId";

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$");

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    private static final String TRACE_ID_PREFIX = "tr";

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final FoundationProperties foundationProperties;

    public TraceIdFilter(FoundationProperties foundationProperties) {
        this.foundationProperties = foundationProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        RequestContext.set(traceId, foundationProperties.getAppCode());
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        writeBackTraceparent(response, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            RequestContext.clear();
        }
    }

    /** 解析入站 traceId：traceparent → X-Request-Id → 新生成。 */
    private String resolveTraceId(HttpServletRequest request) {
        String traceparent = request.getHeader(HEADER_TRACEPARENT);
        if (traceparent != null) {
            Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim());
            if (matcher.matches() && !isAllZero(matcher.group(1))) {
                return matcher.group(1);
            }
        }
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId != null && !requestId.isBlank() && requestId.length() <= MAX_REQUEST_ID_LENGTH) {
            return requestId;
        }
        return Ids.newId(TRACE_ID_PREFIX);
    }

    /**
     * 回写 traceparent：仅在 traceId 为合法 32 位 hex（即源自入站 traceparent）时输出，
     * 避免写出不符合 W3C 的 traceparent；spanId 用新的 16 位 hex，flags 置 {@code 01}。
     */
    private static void writeBackTraceparent(HttpServletResponse response, String traceId) {
        if (TRACE_ID_PATTERN.matcher(traceId).matches()) {
            response.setHeader(HEADER_TRACEPARENT, "00-" + traceId + "-" + newSpanId() + "-01");
        }
    }

    private static String newSpanId() {
        byte[] bytes = new byte[8];
        ThreadLocalRandom.current().nextBytes(bytes);
        StringBuilder spanId = new StringBuilder(16);
        for (byte b : bytes) {
            spanId.append(Character.forDigit((b >> 4) & 0xF, 16));
            spanId.append(Character.forDigit(b & 0xF, 16));
        }
        return spanId.toString();
    }

    private static boolean isAllZero(String hex) {
        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
