package com.foundation.authz.application;

import com.foundation.authz.api.DataScopeApi;
import com.foundation.authz.api.PermissionSubjectView;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.infrastructure.PermissionCodeRepository;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.authz.infrastructure.RoleRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 「谁有这个权限」查询（F-05 BAS-05 §接口 {@code GET /api/authz/who}）。
 *
 * <p>反查持有指定权限码的角色及其授予对象：先确认权限码在目标应用在线，再取含该权限码的角色，
 * 最后列出落在这批角色上的授权并附各角色数据范围。输出按角色编码、对象类型、对象 ID 稳定排序。
 */
public class PermissionSubjectQueryService {

    private final PermissionCodeRepository permissionCodes;
    private final RoleRepository roles;
    private final RoleGrantRepository grants;
    private final AppIdResolver appIds;
    private final DataScopeApi dataScopes;

    public PermissionSubjectQueryService(PermissionCodeRepository permissionCodes, RoleRepository roles,
                                         RoleGrantRepository grants, AppIdResolver appIds,
                                         DataScopeApi dataScopes) {
        this.permissionCodes = permissionCodes;
        this.roles = roles;
        this.grants = grants;
        this.appIds = appIds;
        this.dataScopes = dataScopes;
    }

    /** 列出持有 {@code permissionCode} 的角色与授予对象（{@code appCode} 为空时跨应用）。 */
    public PermissionSubjectView whoHas(String permissionCode, String appCode) {
        Optional<String> appId = appIds.resolve(appCode);

        List<PermissionCode> catalog = appId.isPresent()
                ? permissionCodes.listByApp(appId.get())
                : permissionCodes.listActive();
        boolean known = catalog.stream()
                .anyMatch(code -> code.code().equals(permissionCode) && code.active());
        if (!known) {
            return new PermissionSubjectView(permissionCode, List.of());
        }

        Set<String> roleCodes = new LinkedHashSet<>();
        for (Role role : roles.list()) {
            if (!role.permissions().contains(permissionCode)) {
                continue;
            }
            if (appCode == null || appCode.isBlank() || role.systemScoped()
                    || appId.get().equals(role.appId())) {
                roleCodes.add(role.code());
            }
        }

        List<PermissionSubjectView.Entry> subjects = new ArrayList<>();
        for (RoleGrant grant : grants.list(null, null)) {
            if (!roleCodes.contains(grant.roleCode())) {
                continue;
            }
            subjects.add(new PermissionSubjectView.Entry(grant.roleCode(), grant.subjectType().name(),
                    grant.subjectId(), grant.includeDescendants(),
                    dataScopes.roleDataScope(grant.roleCode())));
        }
        subjects.sort(Comparator.comparing(PermissionSubjectView.Entry::roleCode)
                .thenComparing(PermissionSubjectView.Entry::subjectType)
                .thenComparing(PermissionSubjectView.Entry::subjectId));
        return new PermissionSubjectView(permissionCode, subjects);
    }
}
