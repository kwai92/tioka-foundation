package com.foundation.authz.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 受影响人员判定（F-06 BAS-07 §快照计算）：五类变化各自只圈定应重算的人员。 */
class AffectedPersonResolverTest {

    private final MembershipStub membership = new MembershipStub();
    private final AffectedPersonResolver resolver = new AffectedPersonResolver(membership);

    @Test
    void personGrantAffectsThatPersonOnly() {
        assertThat(resolver.forGrant(grant("r.a", GrantSubjectType.PERSON, "user_1", false)))
                .containsExactly("user_1");
    }

    @Test
    void orgUnitGrantWithDescendantsAffectsPersonsUnderSubtree() {
        membership.descendants.put("org_root", Set.of("org_root", "org_child"));
        membership.personsInOrgUnit.put("org_root", Set.of("user_1"));
        membership.personsInOrgUnit.put("org_child", Set.of("user_2"));

        assertThat(resolver.forGrant(grant("r.a", GrantSubjectType.ORG_UNIT, "org_root", true)))
                .containsExactlyInAnyOrder("user_1", "user_2");
    }

    @Test
    void orgUnitGrantWithoutDescendantsAffectsOwnUnitOnly() {
        membership.personsInOrgUnit.put("org_root", Set.of("user_1"));
        membership.personsInOrgUnit.put("org_child", Set.of("user_2"));

        assertThat(resolver.forGrant(grant("r.a", GrantSubjectType.ORG_UNIT, "org_root", false)))
                .containsExactly("user_1");
    }

    @Test
    void postGrantAffectsPostHolders() {
        membership.personsInPost.put("post_a", Set.of("user_1", "user_2"));

        assertThat(resolver.forGrant(grant("r.a", GrantSubjectType.POST, "post_a", false)))
                .containsExactlyInAnyOrder("user_1", "user_2");
    }

    @Test
    void roleDefinitionChangeUnionsAllGrantsOfThatRole() {
        List<RoleGrant> grants = List.of(
                grant("r.a", GrantSubjectType.PERSON, "user_1", false),
                grant("r.a", GrantSubjectType.PERSON, "user_2", false),
                grant("r.b", GrantSubjectType.PERSON, "user_3", false));

        assertThat(resolver.forRoleDefinition("r.a", grants)).containsExactlyInAnyOrder("user_1", "user_2");
    }

    @Test
    void membershipChangeAffectsOnlyThatPerson() {
        assertThat(resolver.forMembershipChange("user_9")).containsExactly("user_9");
    }

    @Test
    void orgTreeChangeAffectsPersonsUnderSubtree() {
        membership.descendants.put("org_root", Set.of("org_root", "org_child"));
        membership.personsInOrgUnit.put("org_root", Set.of("user_1"));
        membership.personsInOrgUnit.put("org_child", Set.of("user_2"));

        assertThat(resolver.forOrgTreeChange("org_root")).containsExactlyInAnyOrder("user_1", "user_2");
    }

    @Test
    void manifestChangeAffectsAllRegisteredPersons() {
        assertThat(resolver.forManifestChange(List.of("user_1", "user_2"))).containsExactlyInAnyOrder("user_1", "user_2");
        assertThat(resolver.forManifestChange(null)).isEmpty();
    }

    private static RoleGrant grant(String roleCode, GrantSubjectType type, String subjectId, boolean includeDesc) {
        return new RoleGrant("grt_x", roleCode, type, subjectId, includeDesc, null, null, GrantSource.MANUAL);
    }
}
