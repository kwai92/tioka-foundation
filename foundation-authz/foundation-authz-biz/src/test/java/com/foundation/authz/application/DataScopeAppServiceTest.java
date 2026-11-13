package com.foundation.authz.application;

import com.foundation.authz.api.DataScopeView;
import com.foundation.authz.api.PersonDimensionCmd;
import com.foundation.authz.api.RoleDataScopeCmd;
import com.foundation.authz.api.RoleDataScopeView;
import com.foundation.authz.api.ScopeCmd;
import com.foundation.authz.domain.DataDimension;
import com.foundation.authz.domain.DataScopeResolver;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleScope;
import com.foundation.authz.domain.ValueSource;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 数据权限维护与解析（F-06 BAS-06 §接口 {@code /api/fdn}）。 */
class DataScopeAppServiceTest {

    private static final String PERSON = "user_1";
    private static final String APP = "app_a";
    private static final String ROLE = "r.a";

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
    private final TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
    private final TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();
    private final TestDoubles.InMemoryRoleDataScopeRepository roleScopes =
            new TestDoubles.InMemoryRoleDataScopeRepository();
    private final TestDoubles.InMemoryRoleDimensionValueRepository roleDimensionValues =
            new TestDoubles.InMemoryRoleDimensionValueRepository();
    private final TestDoubles.InMemoryPersonDimensionRepository personDimensions =
            new TestDoubles.InMemoryPersonDimensionRepository();
    private final TestDoubles.InMemoryDataDimensionRepository dimensions =
            new TestDoubles.InMemoryDataDimensionRepository();

    private DataScopeAppService service;

    @BeforeEach
    void setUp() {
        apps.register("app_a", APP);
        roles.insert(new Role(ROLE, ROLE, RoleScope.APP, "app_a", false, null, Set.of()));
        grants.insert(new RoleGrant("grt_1", ROLE, GrantSubjectType.PERSON, PERSON, false, null, null,
                GrantSource.MANUAL));
        dimensions.insert(new DataDimension("fdd_region", null, "region", "地区", ValueSource.APP_REPORTED, false));
        EffectiveRoleResolver effectiveRoles = new EffectiveRoleResolver(grants, mutexes, roles,
                membership, new AppIdResolver(apps), new RoleMutexChecker());
        service = new DataScopeAppService(roles, roleScopes, roleDimensionValues, personDimensions,
                dimensions, effectiveRoles, membership, new DataScopeResolver(), apps);
    }

    @Test
    void setRoleDataScopeIsReadBack() {
        service.setRoleDataScope(ROLE, new RoleDataScopeCmd("CUSTOM", List.of("org_x"),
                Map.of("region", new ScopeCmd(false, List.of("east")))));

        RoleDataScopeView view = service.roleDataScope(ROLE);

        assertThat(view.deptScope()).isEqualTo("CUSTOM");
        assertThat(view.deptUnits()).containsExactly("org_x");
        assertThat(view.dimensions().get("region").all()).isFalse();
        assertThat(view.dimensions().get("region").values()).containsExactly("east");
    }

    @Test
    void setRoleDataScopeOverwritesPreviousValues() {
        service.setRoleDataScope(ROLE, new RoleDataScopeCmd("CUSTOM", List.of("org_x"),
                Map.of("region", new ScopeCmd(false, List.of("east")))));

        service.setRoleDataScope(ROLE, new RoleDataScopeCmd("ALL", List.of(),
                Map.of("region", new ScopeCmd(true, List.of()))));

        RoleDataScopeView view = service.roleDataScope(ROLE);
        assertThat(view.deptScope()).isEqualTo("ALL");
        assertThat(view.dimensions().get("region").all()).isTrue();
        assertThat(roleDimensionValues.listByRole(ROLE)).hasSize(1);
    }

    @Test
    void resolveMergesRoleScopeAndDimensionValues() {
        service.setRoleDataScope(ROLE, new RoleDataScopeCmd("CUSTOM", List.of("org_x"),
                Map.of("region", new ScopeCmd(false, List.of("east")))));

        DataScopeView view = service.resolve(PERSON, APP, null);

        assertThat(view.deptScope().values()).containsExactly("org_x");
        assertThat(view.dimensions().get("region").values()).containsExactly("east");
    }

    @Test
    void personGrantIsIncludedInResolve() {
        service.grantPersonDimension(PERSON, new PersonDimensionCmd("region", false, List.of("south"), "MANUAL"));

        DataScopeView view = service.resolve(PERSON, APP, null);

        assertThat(view.dimensions().get("region").values()).contains("south");
    }

    @Test
    void manualPersonGrantCannotBeOverriddenAndCanBeRevoked() {
        service.grantPersonDimension(PERSON, new PersonDimensionCmd("region", false, List.of("south"), "MANUAL"));

        assertThatThrownBy(() -> service.grantPersonDimension(PERSON,
                new PersonDimensionCmd("region", false, List.of("north"), "SYNC")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        service.revokePersonDimension(PERSON, "region");
        assertThat(personDimensions.listByPerson(PERSON)).isEmpty();
    }
}
