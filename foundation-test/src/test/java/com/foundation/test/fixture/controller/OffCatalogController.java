package com.foundation.test.fixture.controller;

import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则 4 反例：权限码 {@code fdn:demo:write} 不在常量目录中。
 */
@RestController
public class OffCatalogController {

    @RequirePermission("fdn:demo:write")
    public String write() {
        return "ok";
    }
}
