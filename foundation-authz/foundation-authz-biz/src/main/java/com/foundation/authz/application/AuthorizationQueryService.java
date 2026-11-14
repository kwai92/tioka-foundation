package com.foundation.authz.application;

import com.foundation.authz.domain.PermissionCatalog;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.Role;
import com.foundation.authz.infrastructure.PermissionCodeRepository;
import com.foundation.authz.infrastructure.RoleRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 授权结果查询（F-05 BAS-05 §有效角色与互斥；供 BAS-07 生成授权快照）。
 *
 * <p>有效角色 → 权限码集合 = 各角色权限码并集 ∩ 应用在线权限码；全局角色只贡献 {@code fdn:*}。
 */
public class AuthorizationQueryService {

    private final EffectiveRoleResolver resolver;
    private final RoleRepository roles;
    private final PermissionCodeRepository permissions;
    private final AppIdResolver appIds;
    private final FeatureToggleGate toggles;

    public AuthorizationQueryService(EffectiveRoleResolver resolver, RoleRepository roles,
                                     PermissionCodeRepository permissions, AppIdResolver appIds,
                                     FeatureToggleGate toggles) {
        this.resolver = resolver;
        this.roles = roles;
        this.permissions = permissions;
        this.appIds = appIds;
        this.toggles = toggles;
    }

    /** 人员在指定应用、指定时刻可用的权限码集合（已剔除功能开关停用范围内的权限码）。 */
    public Set<String> permissionCodes(String personId, String appCode, Instant at) {
        Optional<String> appId = appIds.resolve(appCode);

        Set<String> result = new LinkedHashSet<>();
        for (String roleCode : resolver.resolve(personId, appCode, at)) {
            roles.find(roleCode).ifPresent(role -> collect(role, result));
        }
        if (appId.isPresent()) {
            Set<String> available = activeCodes(appId.get());
            available.removeAll(toggles.disabledPermissionCodes(appId.get()));
            result.retainAll(available);
        }
        return result;
    }

    private Set<String> activeCodes(String appId) {
        Set<String> codes = new LinkedHashSet<>();
        for (PermissionCode permission : permissions.listByApp(appId)) {
            if (permission.active()) {
                codes.add(permission.code());
            }
        }
        return codes;
    }

    private static void collect(Role role, Set<String> target) {
        for (String code : role.permissions()) {
            if (!role.systemScoped() || PermissionCatalog.platformCode(code)) {
                target.add(code);
            }
        }
    }
}
