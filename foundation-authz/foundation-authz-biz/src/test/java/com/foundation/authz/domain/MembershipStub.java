package com.foundation.authz.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 数据权限单测用的人员组织归属桩（F-06 BAS-06 §部门五档展开）。 */
final class MembershipStub implements SubjectMembership {

    final Map<String, Set<String>> orgUnits = new LinkedHashMap<>();
    final Map<String, Set<String>> descendants = new LinkedHashMap<>();

    @Override
    public Set<String> orgUnitsOf(String personId) {
        return orgUnits.getOrDefault(personId, Set.of());
    }

    @Override
    public Set<String> postsOf(String personId) {
        return Set.of();
    }

    @Override
    public Set<String> descendantsOf(String orgUnitId) {
        return descendants.getOrDefault(orgUnitId, Set.of(orgUnitId));
    }
}
