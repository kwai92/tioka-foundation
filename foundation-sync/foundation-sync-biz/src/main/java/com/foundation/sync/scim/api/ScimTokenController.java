package com.foundation.sync.scim.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.sync.scim.api.dto.ScimTokenCreateCmd;
import com.foundation.sync.scim.api.dto.ScimTokenView;
import com.foundation.sync.scim.application.ScimTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * SCIM 令牌管理面（F-08 BAS-13 §5）：{@code /api/scim/tokens}。
 *
 * <p>用管理面权限码 {@code fdn:sync:admin}（V25 已种子）；令牌明文只在创建响应中出现一次。
 * 本类不标 {@code @RestController}：由 {@code ScimAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/scim/tokens")
public class ScimTokenController {

    private final ScimTokenService tokens;

    public ScimTokenController(ScimTokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping
    @RequirePermission("fdn:sync:admin")
    public List<ScimTokenView> list() {
        return tokens.list();
    }

    @PostMapping
    @RequirePermission("fdn:sync:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public ScimTokenView create(@RequestBody ScimTokenCreateCmd cmd) {
        return tokens.issue(cmd, operator());
    }

    @DeleteMapping("/{id}")
    @RequirePermission("fdn:sync:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        tokens.delete(id);
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
