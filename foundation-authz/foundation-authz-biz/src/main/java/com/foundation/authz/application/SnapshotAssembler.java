package com.foundation.authz.application;

import com.foundation.authz.api.DataScopeApi;
import com.foundation.authz.api.DataScopeView;
import com.foundation.authz.api.ScopeView;
import com.foundation.authz.domain.AuthzSnapshot;
import com.foundation.authz.domain.DataScopeResolution;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.SnapshotMenu;
import com.foundation.authz.domain.SnapshotPerson;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.authz.infrastructure.MenuRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 授权快照汇总（F-06 BAS-07 §粒度与内容）。
 *
 * <p>汇总有效角色（BAS-05）、权限码、按权限过滤后的可见菜单、数据范围（BAS-06）
 * 与人员属性（BAS-02 经 {@link SubjectMembership} 端口），产出 {@link AuthzSnapshot}。
 */
public class SnapshotAssembler {

    private final EffectiveRoleResolver effectiveRoles;
    private final AuthorizationQueryService authorizations;
    private final MenuRepository menus;
    private final DataScopeApi dataScopes;
    private final SubjectMembership membership;
    private final AppIdResolver appIds;

    public SnapshotAssembler(EffectiveRoleResolver effectiveRoles, AuthorizationQueryService authorizations,
                             MenuRepository menus, DataScopeApi dataScopes, SubjectMembership membership,
                             AppIdResolver appIds) {
        this.effectiveRoles = effectiveRoles;
        this.authorizations = authorizations;
        this.menus = menus;
        this.dataScopes = dataScopes;
        this.membership = membership;
        this.appIds = appIds;
    }

    /** 计算某人员在某应用、指定版本号的快照。 */
    public AuthzSnapshot assemble(String personId, String appCode, long version) {
        Instant at = Instant.now();
        Set<String> roles = effectiveRoles.resolve(personId, appCode, at);
        Set<String> permissions = authorizations.permissionCodes(personId, appCode, at);
        List<SnapshotMenu> menuTree = menuTree(appIds.resolve(appCode).orElse(null), permissions);
        DataScopeView scope = dataScopes.resolve(personId, appCode, at);

        return new AuthzSnapshot(personId, appCode, version, roles, permissions, menuTree,
                toScope(scope.deptScope()), toDimensions(scope.dimensions()), personOf(personId), at);
    }

    /** 可见菜单树：在线且（无权限码或权限码已授权）；上级不可见时该节点上提为根。 */
    private List<SnapshotMenu> menuTree(String appId, Set<String> permissions) {
        if (appId == null) {
            return List.of();
        }
        List<Menu> visible = menus.listByApp(appId).stream()
                .filter(menu -> LifecycleStatus.ACTIVE.equals(menu.status()))
                .filter(menu -> visibleByPermission(menu, permissions))
                .toList();

        Set<String> visibleIds = new LinkedHashSet<>();
        visible.forEach(menu -> visibleIds.add(menu.id()));
        Map<String, List<Menu>> byParent = new LinkedHashMap<>();
        for (Menu menu : visible) {
            byParent.computeIfAbsent(menu.parentId() == null ? "" : menu.parentId(), key -> new ArrayList<>())
                    .add(menu);
        }

        List<SnapshotMenu> roots = new ArrayList<>();
        for (Menu menu : visible) {
            if (menu.parentId() == null || !visibleIds.contains(menu.parentId())) {
                roots.add(node(menu, byParent));
            }
        }
        return roots;
    }

    private SnapshotMenu node(Menu menu, Map<String, List<Menu>> byParent) {
        List<SnapshotMenu> children = byParent.getOrDefault(menu.id(), List.of()).stream()
                .map(child -> node(child, byParent)).toList();
        return new SnapshotMenu(menu.code(), menu.type(), children);
    }

    private static boolean visibleByPermission(Menu menu, Set<String> permissions) {
        String code = menu.permissionCode();
        return code == null || code.isBlank() || permissions.contains(code);
    }

    private SnapshotPerson personOf(String personId) {
        Set<String> units = new TreeSet<>(membership.orgUnitsOf(personId));
        Set<String> posts = new TreeSet<>(membership.postsOf(personId));
        return new SnapshotPerson(units.isEmpty() ? null : units.iterator().next(), List.copyOf(posts), null);
    }

    private static DataScopeResolution.Scope toScope(ScopeView view) {
        return view.all() ? DataScopeResolution.Scope.ofAll() : DataScopeResolution.Scope.of(view.values());
    }

    private static Map<String, DataScopeResolution.Scope> toDimensions(Map<String, ScopeView> dimensions) {
        Map<String, DataScopeResolution.Scope> result = new LinkedHashMap<>();
        dimensions.forEach((code, scope) -> result.put(code, toScope(scope)));
        return result;
    }
}
