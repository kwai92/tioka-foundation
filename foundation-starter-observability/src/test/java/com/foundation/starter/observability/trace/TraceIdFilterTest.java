package com.foundation.starter.observability.trace;

import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** traceId 透传过滤器（F-02 BAS-24 §foundation-starter-observability）。 */
class TraceIdFilterTest {

    private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final TraceIdFilter filter = new TraceIdFilter(properties());

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        MDC.clear();
    }

    @Test
    void generatesTraceIdWhenNoInboundContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).matches("tr_[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(RequestContext.traceId()).isEmpty();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void reusesTraceIdFromValidTraceparent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        request.addHeader("traceparent", "00-" + VALID_TRACE_ID + "-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(VALID_TRACE_ID);
        assertThat(response.getHeader("traceparent")).matches("00-" + VALID_TRACE_ID + "-[0-9a-f]{16}-01");
        assertThat(RequestContext.traceId()).isEmpty();
    }

    @Test
    void ignoresInvalidTraceparent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        request.addHeader("traceparent", "not-a-traceparent");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).matches("tr_[0-9A-HJKMNP-TV-Z]{26}");
    }

    @Test
    void reusesRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        request.addHeader("X-Request-Id", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("req-123");
    }

    private static FoundationProperties properties() {
        FoundationProperties properties = new FoundationProperties();
        properties.setAppCode("tioka");
        return properties;
    }
}
