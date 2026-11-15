package com.foundation.authz.api;

import com.foundation.authz.api.PermissionApi;
import com.foundation.authz.api.PermissionCodeView;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 权限码目录 REST（F-05 BAS-05 §接口 {@code GET /api/permissions}）。
 *
 * <p>登录即可访问，但按应用过滤：只返回用户有权应用内的权限码，避免全域权限码泄露。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link PermissionApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionApi permissions;

    public PermissionController(PermissionApi permissions) {
        this.permissions = permissions;
    }

    /** 应用可用权限码目录（登录即可）。 */
    @GetMapping
    public List<PermissionCodeView> list(@RequestParam(required = false) String app) {
        PrincipalHolder.require();
        return permissions.list(app);
    }
}
