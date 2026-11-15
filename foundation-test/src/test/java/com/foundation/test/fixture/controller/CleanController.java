package com.foundation.test.fixture.controller;

import com.foundation.starter.security.permission.PublicApi;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则 1 正例 + 规则 4 正例：方法级声明权限码或 {@code @PublicApi}，类级不声明。
 */
@RestController
public class CleanController {

    @RequirePermission("fdn:demo:read")
    public String read() {
        return "ok";
    }

    @PublicApi
    public String ping() {
        return "pong";
    }
}
