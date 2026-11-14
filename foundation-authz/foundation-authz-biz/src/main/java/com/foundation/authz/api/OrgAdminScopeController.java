package com.foundation.authz.api;

import com.foundation.authz.api.OrgAdminScopeApi;
import com.foundation.authz.api.OrgAdminScopeCmd;
import com.foundation.authz.api.OrgAdminScopeView;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 组织分级管理员范围 REST（F-05 BAS-27 §接口 {@code GET/PUT /api/org-admin-scopes}）。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link OrgAdminScopeApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/org-admin-scopes")
public class OrgAdminScopeController {

    private final OrgAdminScopeApi scopes;

    public OrgAdminScopeController(OrgAdminScopeApi scopes) {
        this.scopes = scopes;
    }

    @GetMapping
    @RequirePermission("fdn:subsystem:admin")
    public List<OrgAdminScopeView> list(@RequestParam(required = false) String userId) {
        return scopes.list(userId);
    }

    @PutMapping
    @RequirePermission("fdn:subsystem:admin")
    public OrgAdminScopeView upsert(@RequestBody OrgAdminScopeCmd cmd) {
        return scopes.upsert(cmd, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
