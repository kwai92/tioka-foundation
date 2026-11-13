package com.foundation.authz.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 受影响人员判定（F-06 BAS-07 §快照计算）。
 *
 * <p>五类变化各自只重算受影响人员：
 * <ul>
 *   <li>授权变化（BAS-05）：授予对象为人员 → 该人员；组织单元 → 该单元（及下级）下所有任职人员；
 *       岗位 → 该岗位所有任职人员。</li>
 *   <li>任职变化（BAS-02）：该人员。</li>
 *   <li>组织树变化（BAS-02）：该单元（及下级）下的任职人员。</li>
 *   <li>角色定义变化（BAS-05）：该角色授予对象覆盖的全部人员。</li>
 *   <li>清单变化（BAS-05）：该应用的全部已登记人员。</li>
 * </ul>
 *
 * <p>组织单元与岗位的「反向」查询（某人受某单元/岗位影响）经 {@link SubjectMembership} 端口读取，
 * BAS-02 落地前返回空集合。
 */
public class AffectedPersonResolver {

    private final SubjectMembership membership;

    public AffectedPersonResolver(SubjectMembership membership) {
        this.membership = membership;
    }

    /** 授权变化：按授予对象类型解析受影响人员。 */
    public Set<String> forGrant(RoleGrant grant) {
        return switch (grant.subjectType()) {
            case PERSON -> Set.of(grant.subjectId());
            case ORG_UNIT -> personsUnder(grant.includeDescendants()
                    ? membership.descendantsOf(grant.subjectId())
                    : Set.of(grant.subjectId()));
            case POST -> Set.copyOf(membership.personsInPost(grant.subjectId()));
        };
    }

    /** 角色定义变化：该角色全部授予对象覆盖的人员并集。 */
    public Set<String> forRoleDefinition(String roleCode, List<RoleGrant> grants) {
        Set<String> persons = new LinkedHashSet<>();
        for (RoleGrant grant : grants) {
            if (grant.roleCode().equals(roleCode)) {
                persons.addAll(forGrant(grant));
            }
        }
        return persons;
    }

    /** 任职变化：该人员。 */
    public Set<String> forMembershipChange(String personId) {
        return Set.of(personId);
    }

    /** 组织树变化：该单元（及下级）下的任职人员。 */
    public Set<String> forOrgTreeChange(String orgUnitId) {
        return personsUnder(membership.descendantsOf(orgUnitId));
    }

    /** 清单变化：该应用的全部已登记人员。 */
    public Set<String> forManifestChange(Collection<String> registeredPersons) {
        return registeredPersons == null ? Set.of() : Set.copyOf(registeredPersons);
    }

    private Set<String> personsUnder(Collection<String> orgUnitIds) {
        Set<String> persons = new LinkedHashSet<>();
        for (String orgUnitId : orgUnitIds) {
            persons.addAll(membership.personsInOrgUnit(orgUnitId));
        }
        return persons;
    }
}
