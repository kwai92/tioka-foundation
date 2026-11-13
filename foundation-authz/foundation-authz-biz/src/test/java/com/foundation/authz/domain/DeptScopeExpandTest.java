package com.foundation.authz.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 部门五档展开与跨角色并集（F-06 BAS-06 §部门五档、§合并规则）。 */
class DeptScopeExpandTest {

    private static final String PERSON = "user_1";

    private final DataScopeResolver resolver = new DataScopeResolver();
    private final MembershipStub membership = new MembershipStub();

    @Test
    void deptTakesPersonOrgUnits() {
        membership.orgUnits.put(PERSON, Set.of("org_a"));

        DataScopeResolution result = resolve(List.of(scope("r.a", DeptScope.DEPT, List.of())));

        assertThat(result.deptScope().all()).isFalse();
        assertThat(result.deptScope().values()).containsExactly("org_a");
    }

    @Test
    void deptAndSubExpandsDescendants() {
        membership.orgUnits.put(PERSON, Set.of("org_a"));
        membership.descendants.put("org_a", Set.of("org_a", "org_b", "org_c"));

        DataScopeResolution result = resolve(List.of(scope("r.a", DeptScope.DEPT_AND_SUB, List.of())));

        assertThat(result.deptScope().values()).containsExactlyInAnyOrder("org_a", "org_b", "org_c");
    }

    @Test
    void customUsesConfiguredUnits() {
        DataScopeResolution result = resolve(List.of(scope("r.a", DeptScope.CUSTOM, List.of("org_x", "org_y"))));

        assertThat(result.deptScope().all()).isFalse();
        assertThat(result.deptScope().values()).containsExactlyInAnyOrder("org_x", "org_y");
    }

    @Test
    void selfYieldsEmptyScope() {
        membership.orgUnits.put(PERSON, Set.of("org_a"));

        DataScopeResolution result = resolve(List.of(scope("r.a", DeptScope.SELF, List.of())));

        assertThat(result.deptScope().all()).isFalse();
        assertThat(result.deptScope().values()).isEmpty();
    }

    @Test
    void allSentinelIsNotErasedByUnion() {
        membership.orgUnits.put(PERSON, Set.of("org_a"));

        DataScopeResolution result = resolve(List.of(
                scope("r.a", DeptScope.DEPT, List.of()),
                scope("r.b", DeptScope.ALL, List.of())));

        assertThat(result.deptScope().all()).isTrue();
        assertThat(result.deptScope().values()).isEmpty();
    }

    @Test
    void unionsAcrossEffectiveRoles() {
        membership.orgUnits.put(PERSON, Set.of("org_z"));

        DataScopeResolution result = resolve(List.of(
                scope("r.a", DeptScope.CUSTOM, List.of("org_x")),
                scope("r.b", DeptScope.DEPT, List.of())));

        assertThat(result.deptScope().values()).containsExactlyInAnyOrder("org_x", "org_z");
    }

    @Test
    void ignoresRolesThatAreNotEffective() {
        membership.orgUnits.put(PERSON, Set.of("org_a"));

        DataScopeResolution result = resolve(Set.of("r.a"), List.of(
                scope("r.a", DeptScope.DEPT, List.of()),
                scope("r.b", DeptScope.CUSTOM, List.of("org_y"))));

        assertThat(result.deptScope().values()).containsExactly("org_a");
    }

    private DataScopeResolution resolve(List<RoleDataScope> scopes) {
        Set<String> effective = scopes.stream().map(RoleDataScope::roleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return resolve(effective, scopes);
    }

    private DataScopeResolution resolve(Set<String> effective, List<RoleDataScope> scopes) {
        return resolver.resolve(PERSON, effective, scopes, List.of(), List.of(), Map.of(), membership);
    }

    private static RoleDataScope scope(String roleCode, DeptScope deptScope, List<String> units) {
        return new RoleDataScope("frs_" + roleCode, roleCode, deptScope, units);
    }
}
