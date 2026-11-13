package com.foundation.authz.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 数据权限与授权快照单测用的人员组织归属桩（F-06 BAS-06/BAS-07）。 */
final class MembershipStub implements SubjectMembership {

    final Map<String, Set<String>> orgUnits = new LinkedHashMap<>();
    final Map<String, Set<String>> descendants = new LinkedHashMap<>();
    final Map<String, Set<String>> posts = new LinkedHashMap<>();
    final Map<String, Set<String>> personsInOrgUnit = new LinkedHashMap<>();
    final Map<String, Set<String>> personsInPost = new LinkedHashMap<>();

    @Override
    public Set<String> orgUnitsOf(String personId) {
        return orgUnits.getOrDefault(personId, Set.of());
    }

    @Override
    public Set<String> postsOf(String personId) {
        return posts.getOrDefault(personId, Set.of());
    }

    @Override
    public Set<String> descendantsOf(String orgUnitId) {
        return descendants.getOrDefault(orgUnitId, Set.of(orgUnitId));
    }

    @Override
    public Set<String> personsInOrgUnit(String orgUnitId) {
        return personsInOrgUnit.getOrDefault(orgUnitId, Set.of());
    }

    @Override
    public Set<String> personsInPost(String postId) {
        return personsInPost.getOrDefault(postId, Set.of());
    }
}
