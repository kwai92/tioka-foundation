package com.foundation.authz.application;

import com.foundation.authz.api.DataScopeView;
import com.foundation.authz.api.ScopeView;
import com.foundation.authz.domain.AuthzSnapshot;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 授权快照汇总（F-06 BAS-07 §粒度与内容）。 */
class SnapshotAssemblerTest {

    private static final String PERSON = "user_1";
    private static final String APP = "app_a";
    private static final String ROLE = "r.a";

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
    private final TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
    private final TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
    private final TestDoubles.InMemoryPermissionCodeRepository permissionCodes =
            new TestDoubles.InMemoryPermissionCodeRepository();
    private final TestDoubles.InMemoryMenuRepository menus = new TestDoubles.InMemoryMenuRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();
    private final TestDoubles.StubDataScopeApi dataScopes = new TestDoubles.StubDataScopeApi();

    private SnapshotAssembler assembler;

    @BeforeEach
    void setUp() {
        apps.register("app_a", APP);
        roles.insert(new Role(ROLE, ROLE, RoleScope.APP, "app_a", false, null, Set.of("a:read")));
        grants.insert(new RoleGrant("grt_1", ROLE, GrantSubjectType.PERSON, PERSON, false, null, null,
                GrantSource.MANUAL));
        permissionCodes.insert(permission("pms_1", "a:read"));
        permissionCodes.insert(permission("pms_2", "a:write"));

        // 菜单：根 → 子（权限 a:read）；根 → 密（权限 a:write，未授予，隐藏）→ 孙（无权限码，上提为根）
        menus.insert(menu("men_root", "root", null, null));
        menus.insert(menu("men_child", "child", "men_root", "a:read"));
        menus.insert(menu("men_secret", "secret", "men_root", "a:write"));
        menus.insert(menu("men_grand", "grand", "men_secret", null));

        membership.orgUnits.put(PERSON, Set.of("org_b", "org_a"));
        membership.posts.put(PERSON, Set.of("post_2", "post_1"));
        dataScopes.view = new DataScopeView(ScopeView.of(List.of("org_b", "org_a")),
                Map.of("region", ScopeView.ofAll()));

        AppIdResolver appIds = new AppIdResolver(apps);
        EffectiveRoleResolver effectiveRoles = new EffectiveRoleResolver(grants, mutexes, roles, membership,
                appIds, new RoleMutexChecker());
        FeatureToggleGate toggles = new FeatureToggleGate(new TestDoubles.StubFeatureToggleApi(),
                new TestDoubles.InMemorySubsystemRepository(), new TestDoubles.InMemoryModuleRepository(),
                new TestDoubles.InMemoryFunctionRepository(), menus);
        AuthorizationQueryService authorizations = new AuthorizationQueryService(effectiveRoles, roles,
                permissionCodes, appIds, toggles);
        assembler = new SnapshotAssembler(effectiveRoles, authorizations, menus, dataScopes, membership, appIds,
                toggles);
    }

    @Test
    void assemblesRolesPermissionsAndMenusFilteredByPermission() {
        AuthzSnapshot snapshot = assembler.assemble(PERSON, APP, 7);

        assertThat(snapshot.version()).isEqualTo(7);
        assertThat(snapshot.roles()).containsExactly(ROLE);
        assertThat(snapshot.permissions()).containsExactly("a:read");
        assertThat(snapshot.menus()).extracting("code").containsExactly("root", "grand");
        assertThat(snapshot.menus().get(0).children()).extracting("code").containsExactly("child");
    }

    @Test
    void nodeWhoseParentIsInvisibleIsPromotedToRoot() {
        AuthzSnapshot snapshot = assembler.assemble(PERSON, APP, 1);

        assertThat(snapshot.menus()).extracting("code").contains("grand");
    }

    @Test
    void carriesDataScopeAndPersonAttributes() {
        AuthzSnapshot snapshot = assembler.assemble(PERSON, APP, 1);

        assertThat(snapshot.deptScope().all()).isFalse();
        assertThat(snapshot.deptScope().values()).containsExactlyInAnyOrder("org_a", "org_b");
        assertThat(snapshot.dimensions().get("region").all()).isTrue();
        assertThat(snapshot.person().primaryOrgUnit()).isEqualTo("org_a");
        assertThat(snapshot.person().positions()).containsExactly("post_1", "post_2");
    }

    private static PermissionCode permission(String id, String code) {
        return new PermissionCode(id, "app_a", code, code, null, LifecycleStatus.ACTIVE, null, null);
    }

    private static Menu menu(String id, String code, String parentId, String permissionCode) {
        return new Menu(id, "app_a", code, code, "MENU", parentId, null, null, null, 0, true, permissionCode,
                LifecycleStatus.ACTIVE);
    }
}
