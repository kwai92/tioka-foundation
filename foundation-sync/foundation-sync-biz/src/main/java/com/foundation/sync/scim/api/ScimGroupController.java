package com.foundation.sync.scim.api;

import com.foundation.starter.security.permission.PublicApi;
import com.foundation.sync.scim.application.ScimGroupService;
import com.foundation.sync.scim.application.ScimTokenService;
import com.foundation.sync.scim.domain.ScimScope;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

/**
 * SCIM 2.0 Groups 服务端（F-08 BAS-13 §1）：{@code /scim/v2/Groups}（映射有层级的 {@code fdn_org_unit}）。
 *
 * <p>认证与作用域同 {@link ScimUserController}。本类不标 {@code @RestController}：由
 * {@code ScimAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/scim/v2/Groups")
public class ScimGroupController {

    private final ScimGroupService groups;
    private final ScimTokenService tokens;

    public ScimGroupController(ScimGroupService groups, ScimTokenService tokens) {
        this.groups = groups;
        this.tokens = tokens;
    }

    @GetMapping
    @PublicApi
    public Map<String, Object> list(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @RequestParam(required = false) String filter,
                                    @RequestParam(required = false) Integer startIndex,
                                    @RequestParam(required = false) Integer count,
                                    @RequestParam(required = false) String sortBy,
                                    @RequestParam(required = false) String sortOrder) {
        tokens.requireScope(tokens.authenticate(authorization), ScimScope.GROUPS, false);
        return groups.list(filter, startIndex, count, sortBy, sortOrder);
    }

    @GetMapping("/{id}")
    @PublicApi
    public Map<String, Object> get(@RequestHeader(name = "Authorization", required = false) String authorization,
                                   @PathVariable String id) {
        tokens.requireScope(tokens.authenticate(authorization), ScimScope.GROUPS, false);
        return groups.get(id);
    }

    @PostMapping
    @PublicApi
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestHeader(name = "Authorization", required = false) String authorization,
                                      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                      @RequestBody Map<String, Object> payload) {
        tokens.requireScope(tokens.authenticate(authorization), ScimScope.GROUPS, true);
        return groups.create(payload, idempotencyKey);
    }

    @PutMapping("/{id}")
    @PublicApi
    public Map<String, Object> replace(@RequestHeader(name = "Authorization", required = false) String authorization,
                                       @PathVariable String id,
                                       @RequestBody Map<String, Object> payload) {
        tokens.requireScope(tokens.authenticate(authorization), ScimScope.GROUPS, true);
        return groups.replace(id, payload);
    }

    @PatchMapping("/{id}")
    @PublicApi
    public Map<String, Object> patch(@RequestHeader(name = "Authorization", required = false) String authorization,
                                     @PathVariable String id,
                                     @RequestBody Map<String, Object> body) {
        tokens.requireScope(tokens.authenticate(authorization), ScimScope.GROUPS, true);
        return groups.patch(id, body);
    }

    @DeleteMapping("/{id}")
    @PublicApi
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(name = "Authorization", required = false) String authorization,
                       @PathVariable String id) {
        tokens.requireScope(tokens.authenticate(authorization), ScimScope.GROUPS, true);
        groups.delete(id);
    }
}
