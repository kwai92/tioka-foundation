package com.foundation.starter.web.filter;

import com.foundation.starter.web.config.FoundationWebProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 请求访问日志过滤器（F-02 BAS-24 §foundation-starter-web）：正常请求记录日志且不抛异常。 */
class RequestLogFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LogProbeController())
                .addFilters(new RequestLogFilter(new FoundationWebProperties()))
                .build();
    }

    @Test
    void normalRequestPassesThrough() throws Exception {
        mockMvc.perform(get("/ping").queryParam("password", "secret").header("Authorization", "Bearer x"))
                .andExpect(status().isOk());
    }

    /** 日志测试探针控制器。 */
    @RestController
    static class LogProbeController {

        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }
}
