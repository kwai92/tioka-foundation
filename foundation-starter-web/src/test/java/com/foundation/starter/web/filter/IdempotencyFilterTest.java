package com.foundation.starter.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.web.config.FoundationWebProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 幂等过滤器（F-02 BAS-24 §foundation-starter-web）：首次放行、重复 409、非写/无头忽略、失败可重试。 */
class IdempotencyFilterTest {

    private static final String KEY = "Idempotency-Key";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FoundationWebProperties properties = new FoundationWebProperties();
        IdempotencyStore store = new InMemoryIdempotencyStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new IdempotencyProbeController())
                .addFilters(new IdempotencyFilter(store, properties, new ObjectMapper()))
                .build();
    }

    @Test
    void firstRequestIsAllowed() throws Exception {
        mockMvc.perform(post("/create").header(KEY, "k-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void duplicateRequestIsRejectedWith409() throws Exception {
        mockMvc.perform(post("/create").header(KEY, "k-2")).andExpect(status().isOk());

        mockMvc.perform(post("/create").header(KEY, "k-2"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("重复请求")))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void nonWriteMethodIsIgnored() throws Exception {
        mockMvc.perform(get("/ping").header(KEY, "k-3")).andExpect(status().isOk());
        mockMvc.perform(get("/ping").header(KEY, "k-3")).andExpect(status().isOk());
    }

    @Test
    void requestWithoutHeaderIsIgnored() throws Exception {
        mockMvc.perform(post("/create")).andExpect(status().isOk());
        mockMvc.perform(post("/create")).andExpect(status().isOk());
    }

    @Test
    void failedRequestCanBeRetriedWithSameKey() throws Exception {
        mockMvc.perform(post("/flaky").header(KEY, "k-4")).andExpect(status().isInternalServerError());

        mockMvc.perform(post("/flaky").header(KEY, "k-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    /** 幂等测试探针控制器。 */
    @RestController
    static class IdempotencyProbeController {

        private final AtomicInteger flakyCalls = new AtomicInteger();

        @PostMapping("/create")
        public Map<String, Object> create() {
            return Map.of("ok", true);
        }

        @GetMapping("/ping")
        public Map<String, Object> ping() {
            return Map.of("ok", true);
        }

        @PostMapping("/flaky")
        public Map<String, Object> flaky(HttpServletResponse response) {
            if (flakyCalls.incrementAndGet() == 1) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return Map.of("ok", false);
            }
            return Map.of("ok", true);
        }
    }
}
