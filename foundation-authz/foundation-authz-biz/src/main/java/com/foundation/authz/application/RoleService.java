package com.foundation.authz.application;

import com.foundation.authz.api.RoleApi;
import com.foundation.authz.api.RoleCmd;
import com.foundation.authz.api.RoleView;
import com.foundation.authz.domain.PermissionCatalog;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleScope;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.authz.infrastructure.RoleRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 角色维护（F-05 BAS-05 §接口 {@code GET/POST/PUT/DELETE /api/roles}）。
 *
 * <p>硬规则：内置角色不可删；全局角色（{@code SYSTEM}）只允许含 {@code fdn:*} 权限码；
 * 角色仍被授权引用时删除返回 {@code CONFLICT}；编码创建后不可改。
 */
public class RoleService implements RoleApi {

    private final RoleRepository roles;
    private final RoleGrantRepository grants;
    private final AppIdResolver appIds;

    public RoleService(RoleRepository roles, RoleGrantRepository grants, AppIdResolver appIds) {
        this.roles = roles;
        this.grants = grants;
        this.appIds = appIds;
    }

    @Override
    public List<RoleView> list(String appCode) {
        Optional<String> appId = appIds.resolve(appCode);
        return roles.list().stream()
                .filter(role -> appId.isEmpty() || role.systemScoped() || appId.get().equals(role.appId()))
                .map(RoleService::toView)
                .toList();
    }

    @Override
    @Transactional
    public RoleView create(RoleCmd cmd) {
        String code = requireText(cmd.code(), "code");
        RoleScope scope = RoleScope.of(cmd.scope());
        if (roles.find(code).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "角色编码已存在：" + code);
        }
        String appId = scope == RoleScope.APP ? requireText(cmd.appId(), "appId") : null;
        Role role = new Role(code, requireText(cmd.name(), "name"), scope, appId, false, cmd.dataScope(),
                permissionsOf(cmd.permissions(), scope));
        roles.insert(role);
        return toView(role);
    }

    @Override
    @Transactional
    public RoleView update(RoleCmd cmd) {
        String code = requireText(cmd.code(), "code");
        Role existing = roles.find(code)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在：" + code));
        Role updated = new Role(code, requireText(cmd.name(), "name"), existing.scope(), existing.appId(),
                existing.builtIn(), cmd.dataScope(), permissionsOf(cmd.permissions(), existing.scope()));
        roles.update(updated);
        return toView(updated);
    }

    @Override
    @Transactional
    public void delete(String code, String operator) {
        Role existing = roles.find(code)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在：" + code));
        if (existing.builtIn()) {
            throw new BizException(ErrorCode.CONFLICT, "内置角色不可删除：" + code);
        }
        if (grants.countByRole(code) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "角色仍被授权引用，无法删除：" + code);
        }
        roles.delete(code);
    }

    private static Set<String> permissionsOf(List<String> permissions, RoleScope scope) {
        Set<String> codes = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
        codes.forEach(PermissionCatalog::validatePermissionCode);
        if (scope == RoleScope.SYSTEM && !PermissionCatalog.platformScopedOnly(codes)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "全局角色只允许含 fdn:* 权限码");
        }
        return codes;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return value;
    }

    private static RoleView toView(Role role) {
        return new RoleView(role.code(), role.name(), role.scope().name(), role.appId(), role.builtIn(),
                role.dataScope(), List.copyOf(role.permissions()));
    }
}
