package com.foundation.starter.web.error;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 全局异常处理（F-02 BAS-24 §foundation-starter-web）：错误码翻译、参数校验 400、兜底 500 不泄露细节。 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void bizExceptionMapsToErrorCodeStatus() throws Exception {
        mockMvc.perform(get("/biz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("x"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingMessage() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL.getMessage()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("boom"))));
    }

    @Test
    void invalidBodyReturns400WithFieldMessage() throws Exception {
        mockMvc.perform(post("/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(get("/submit"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("不支持的请求方法"));
    }

    @Test
    void rawBadBodyReturns400() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
    }

    /** 测试探针控制器。 */
    @RestController
    static class ProbeController {

        @GetMapping("/biz")
        public String biz() {
            throw new BizException(ErrorCode.NOT_FOUND, "x");
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/valid")
        public String valid(@Valid @RequestBody Payload payload) {
            return payload.getName();
        }

        @PostMapping("/submit")
        public String submit() {
            return "ok";
        }

        @PostMapping("/echo")
        public Payload echo(@RequestBody Payload payload) {
            return payload;
        }
    }

    /** 请求体载体。 */
    static class Payload {

        @NotBlank(message = "不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
