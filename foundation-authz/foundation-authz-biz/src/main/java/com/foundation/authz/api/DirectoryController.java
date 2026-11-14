package com.foundation.authz.api;

import com.foundation.authz.api.DirectoryApi;
import com.foundation.authz.api.DirectoryView;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 应用目录 REST（F-05 BAS-27 §接口 {@code GET /api/subsystems/{code}/directory}）。
 *
 * <p>返回子系统 → 模块 → 功能 → 操作树；节点启用状态受功能开关影响。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link DirectoryApi} 等依赖同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/subsystems/{code}/directory")
public class DirectoryController {

    private final DirectoryApi directories;

    public DirectoryController(DirectoryApi directories) {
        this.directories = directories;
    }

    @GetMapping
    @RequirePermission("fdn:subsystem:read")
    public DirectoryView directory(@PathVariable String code) {
        return directories.directory(code);
    }
}
