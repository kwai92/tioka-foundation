package com.foundation.authz.api;

import com.foundation.authz.api.RoleApi;
import com.foundation.authz.api.RoleCmd;
import com.foundation.authz.api.RoleView;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 角色 REST（F-05 BAS-05 §接口 {@code /api/roles}）：查询、新建、更新与删除。
 *
 * <p>读 {@code fdn:role:read}，写 {@code fdn:role:admin}；内置角色不可删，仍被授权引用时删除返回 {@code CONFLICT}。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link RoleApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleApi roles;

    public RoleController(RoleApi roles) {
        this.roles = roles;
    }

    /** 角色列表（{@code app} 为空时返回全部）。 */
    @GetMapping
    @RequirePermission("fdn:role:read")
    public List<RoleView> list(@RequestParam(required = false) String app) {
        return roles.list(app);
    }

    /** 新建角色。 */
    @PostMapping
    @RequirePermission("fdn:role:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleView create(@RequestBody RoleRequest request) {
        return roles.create(toCmd(request, request.code()));
    }

    /** 更新角色（编码以路径为准，不可改）。 */
    @PutMapping("/{code}")
    @RequirePermission("fdn:role:admin")
    public RoleView update(@PathVariable String code, @RequestBody RoleRequest request) {
        return roles.update(toCmd(request, code));
    }

    /** 删除角色（内置或被授权引用时拒绝）。 */
    @DeleteMapping("/{code}")
    @RequirePermission("fdn:role:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        roles.delete(code, operator());
    }

    private static RoleCmd toCmd(RoleRequest request, String code) {
        return new RoleCmd(code, request.name(), request.scope(), request.appId(), request.dataScope(),
                request.permissions(), operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 角色新建 / 更新请求体。 */
    public record RoleRequest(String code,
                              String name,
                              String scope,
                              String appId,
                              String dataScope,
                              List<String> permissions) {
    }
}
