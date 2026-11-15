package com.foundation.{app}.example.api;

import com.foundation.starter.audit.annotation.Audited;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 示例 REST（BAS-25 §控制器约定）。
 *
 * <p>刻意不标 {@code @RestController}：用 {@code @ResponseBody} + {@code @RequestMapping}，
 * 由 {@code ExampleAutoConfiguration} 以 {@code @Bean} 装配。
 *
 * <p>{@link ExampleApi} 与本类同包（分属 {@code -api} 与 {@code -biz} 两个模块），
 * 故无需 import；控制器只做参数绑定与权限/审计注解，业务在 {@code ExampleService}。
 */
@ResponseBody
@RequestMapping("/api/example")
public class ExampleController {

    private final ExampleApi exampleApi;

    public ExampleController(ExampleApi exampleApi) {
        this.exampleApi = exampleApi;
    }

    @GetMapping("/{id}")
    @RequirePermission("{app}:example:read")
    public ExampleView get(@PathVariable String id) {
        return exampleApi.get(id);
    }

    @PostMapping
    @RequirePermission("{app}:example:admin")
    @Audited(action = "{app}.example.create", resource = "example")
    public ExampleView create(@RequestBody CreateRequest request) {
        return exampleApi.create(request.name());
    }

    /** 新建请求体。 */
    public record CreateRequest(String name) {
    }
}
