package com.foundation.test.fixture.controller;

import org.springframework.web.bind.annotation.RestController;

/**
 * 规则 1 反例：public 方法既无 {@code @RequirePermission} 也无 {@code @PublicApi}（类级同样缺失）。
 */
@RestController
public class UnannotatedController {

    public String leak() {
        return "leak";
    }
}
