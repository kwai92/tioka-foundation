package com.foundation.starter.web.filter;

import com.foundation.starter.core.context.DegradeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** ADP-08：降级标记透传与 ThreadLocal 清理（防线程池复用串标记）。 */
class DegradeHeaderFilterTest {

    private final DegradeHeaderFilter filter = new DegradeHeaderFilter();

    @AfterEach
    void cleanUp() {
        DegradeContext.clear();
    }

    @Test
    void 发生降级时写响应头() throws Exception {
        // 模拟内层业务在 Foundation 不可用时打了标记
        MockFilterChain chain = new MockFilterChain();
        DegradeContext.mark();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), response, chain);

        assertEquals("true", response.getHeader(DegradeContext.HEADER));
        assertFalse(DegradeContext.isMarked(), "请求结束后必须清理标记");
    }

    @Test
    void 未降级时不写响应头() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), response,
                new MockFilterChain());

        assertNull(response.getHeader(DegradeContext.HEADER));
    }

    @Test
    void 连续请求之间标记不串() throws Exception {
        DegradeContext.mark();
        filter.doFilter(new MockHttpServletRequest("GET", "/a"), new MockHttpServletResponse(),
                new MockFilterChain());
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/b"), second, new MockFilterChain());

        assertNull(second.getHeader(DegradeContext.HEADER));
    }
}
