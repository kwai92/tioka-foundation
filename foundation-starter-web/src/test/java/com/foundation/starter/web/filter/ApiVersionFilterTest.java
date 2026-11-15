package com.foundation.starter.web.filter;

import com.foundation.starter.core.contract.FoundationApiVersion;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-03：版本头的兼容、拒绝与错误码语义。 */
class ApiVersionFilterTest {

    private final ApiVersionFilter filter = new ApiVersionFilter();

    private MockHttpServletResponse run(String version) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/foundation/dict/gender");
        if (version != null) {
            request.addHeader(FoundationApiVersion.HEADER, version);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void 缺省版本头视为v1并放行() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/foundation/dict/a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertEquals(FoundationApiVersion.CURRENT, response.getHeader(FoundationApiVersion.HEADER));
        assertTrue(chain.getRequest() != null, "缺省头必须放行");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void v1与数字1都放行() throws Exception {
        for (String value : new String[]{"v1", "1", "V1", " v1 "}) {
            MockHttpServletResponse response = run(value);
            assertEquals(200, response.getStatus(), value);
            assertEquals(FoundationApiVersion.CURRENT,
                    response.getHeader(FoundationApiVersion.HEADER));
        }
    }

    @Test
    void 不支持的主版本返回406与明确错误码() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/apps");
        request.addHeader(FoundationApiVersion.HEADER, "v2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(406, response.getStatus());
        assertNull(chain.getRequest(), "版本不兼容必须短路，不得进入业务链");
        assertTrue(response.getContentAsString().contains(FoundationApiVersion.UNSUPPORTED_CODE));
        assertTrue(response.getContentAsString().contains("\"supported\":\"1\""));
    }

    @Test
    void 格式非法返回400而不是500() throws Exception {
        MockHttpServletResponse response = run("latest");
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains(FoundationApiVersion.MALFORMED_CODE));
    }

    @Test
    void 版本解析常量行为() {
        assertEquals(1, FoundationApiVersion.parseMajor("v1"));
        assertEquals(2, FoundationApiVersion.parseMajor("V2"));
        assertNull(FoundationApiVersion.parseMajor("v0"));
        assertNull(FoundationApiVersion.parseMajor("beta"));
    }
}
