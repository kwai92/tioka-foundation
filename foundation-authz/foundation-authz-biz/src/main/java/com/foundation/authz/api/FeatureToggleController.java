package com.foundation.authz.api;

import com.foundation.authz.api.FeatureToggleApi;
import com.foundation.authz.api.FeatureToggleView;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 功能开关 REST（F-05 BAS-27 §接口 {@code PUT /api/feature-toggles/{scopeType}/{scopeId}}）。
 *
 * <p>停用效果在快照/菜单/接口三处一致；作用域从父向子继承，最具体作用域优先。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link FeatureToggleApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/feature-toggles")
public class FeatureToggleController {

    private final FeatureToggleApi toggles;

    public FeatureToggleController(FeatureToggleApi toggles) {
        this.toggles = toggles;
    }

    @GetMapping
    @RequirePermission("fdn:subsystem:read")
    public List<FeatureToggleView> list() {
        return toggles.list();
    }

    @PutMapping("/{scopeType}/{scopeId}")
    @RequirePermission("fdn:toggle:admin")
    public FeatureToggleView set(@PathVariable String scopeType, @PathVariable String scopeId,
                                 @RequestBody ToggleRequest request) {
        return toggles.set(scopeType, scopeId, request.enabled(), request.reason(), operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 开关设置请求体。 */
    public record ToggleRequest(boolean enabled, String reason) {
    }
}
