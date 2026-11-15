package com.foundation.authz.api;

import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.PermissionSubjectView;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.api.RoleGrantView;
import com.foundation.authz.application.PermissionSubjectQueryService;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.List;

/**
 * 授权 REST（F-05 BAS-05 §接口 {@code /api/grants}）：授权查询、新增、撤销与「谁有这个权限」反查。
 *
 * <p>权限码 {@code fdn:grant:admin}（HIGH）；授予高风险角色需二次确认并审计（BAS-19）。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link GrantApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api")
public class GrantController {

    private final GrantApi grants;
    private final PermissionSubjectQueryService permissionSubjects;

    public GrantController(GrantApi grants, PermissionSubjectQueryService permissionSubjects) {
        this.grants = grants;
        this.permissionSubjects = permissionSubjects;
    }

    /** 授权列表（按授予对象过滤）。 */
    @GetMapping("/grants")
    @RequirePermission("fdn:grant:admin")
    public List<RoleGrantView> list(@RequestParam(required = false) String subjectType,
                                    @RequestParam(required = false) String subjectId) {
        return grants.list(subjectType, subjectId);
    }

    /** 新增授权，返回授权 ID。 */
    @PostMapping("/grants")
    @RequirePermission("fdn:grant:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse grant(@RequestBody GrantRequest request) {
        String id = grants.grant(new RoleGrantCmd(request.roleCode(), request.subjectType(),
                request.subjectId(), request.includeDescendants(), request.effectiveFrom(),
                request.effectiveTo(), request.source(), operator()));
        return new IdResponse(id);
    }

    /** 撤销授权。 */
    @DeleteMapping("/grants/{id}")
    @RequirePermission("fdn:grant:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String id) {
        grants.revoke(id, operator());
    }

    /** 谁有这个权限。 */
    @GetMapping("/authz/who")
    @RequirePermission("fdn:grant:admin")
    public PermissionSubjectView who(@RequestParam String permission,
                                     @RequestParam(required = false) String app) {
        return permissionSubjects.whoHas(permission, app);
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 新增授权请求体。 */
    public record GrantRequest(String roleCode,
                               String subjectType,
                               String subjectId,
                               boolean includeDescendants,
                               Instant effectiveFrom,
                               Instant effectiveTo,
                               String source) {
    }

    /** 授权 ID 响应体。 */
    public record IdResponse(String id) {
    }
}
