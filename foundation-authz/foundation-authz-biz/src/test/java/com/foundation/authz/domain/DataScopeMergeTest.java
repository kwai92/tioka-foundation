package com.foundation.authz.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 维度取值合并（F-06 BAS-06 §合并规则）：跨角色并集，「全部」哨兵不被抹掉。 */
class DataScopeMergeTest {

    private static final String PERSON = "user_1";

    private final DataScopeResolver resolver = new DataScopeResolver();
    private final MembershipStub membership = new MembershipStub();
    private final Map<String, String> dimensionCodeById = Map.of("d_region", "region", "d_store", "store");

    @Test
    void unionsDimensionValuesAcrossRoles() {
        DataScopeResolution result = resolve(Set.of("r.a", "r.b"),
                List.of(roleValue("r.a", "d_region", "east"), roleValue("r.b", "d_region", "west")),
                List.of());

        assertThat(result.dimensions().get("region").all()).isFalse();
        assertThat(result.dimensions().get("region").values()).containsExactlyInAnyOrder("east", "west");
    }

    @Test
    void allSentinelWinsInDimensionUnion() {
        DataScopeResolution result = resolve(Set.of("r.a", "r.b"),
                List.of(roleAll("r.a", "d_region"), roleValue("r.b", "d_region", "east")),
                List.of());

        assertThat(result.dimensions().get("region").all()).isTrue();
        assertThat(result.dimensions().get("region").values()).isEmpty();
    }

    @Test
    void personGrantAddsToRoleValues() {
        DataScopeResolution result = resolve(Set.of("r.a"),
                List.of(roleValue("r.a", "d_region", "east")),
                List.of(personValue("d_region", "south")));

        assertThat(result.dimensions().get("region").values()).containsExactlyInAnyOrder("east", "south");
    }

    @Test
    void personGrantAllMakesDimensionAll() {
        DataScopeResolution result = resolve(Set.of("r.a"),
                List.of(roleValue("r.a", "d_region", "east")),
                List.of(new PersonDimensionGrant("fpd_1", PERSON, "d_region", null, true, GrantSource.MANUAL)));

        assertThat(result.dimensions().get("region").all()).isTrue();
    }

    @Test
    void ignoresDimensionValuesOfNonEffectiveRoles() {
        DataScopeResolution result = resolve(Set.of("r.a"),
                List.of(roleValue("r.a", "d_region", "east"), roleValue("r.b", "d_region", "west")),
                List.of());

        assertThat(result.dimensions().get("region").values()).containsExactly("east");
    }

    @Test
    void unknownDimensionIsIgnored() {
        DataScopeResolution result = resolve(Set.of("r.a"),
                List.of(roleValue("r.a", "d_unknown", "east")),
                List.of());

        assertThat(result.dimensions()).isEmpty();
    }

    private DataScopeResolution resolve(Set<String> effective, List<RoleDimensionValue> roleValues,
                                        List<PersonDimensionGrant> grants) {
        return resolver.resolve(PERSON, effective, List.of(), roleValues, grants, dimensionCodeById, membership);
    }

    private static RoleDimensionValue roleValue(String roleCode, String dimensionId, String valueCode) {
        return new RoleDimensionValue("frv_" + roleCode + "_" + valueCode, roleCode, dimensionId, valueCode, false);
    }

    private static RoleDimensionValue roleAll(String roleCode, String dimensionId) {
        return new RoleDimensionValue("frv_" + roleCode + "_all", roleCode, dimensionId, null, true);
    }

    private static PersonDimensionGrant personValue(String dimensionId, String valueCode) {
        return new PersonDimensionGrant("fpd_" + valueCode, PERSON, dimensionId, valueCode, false, GrantSource.MANUAL);
    }
}
