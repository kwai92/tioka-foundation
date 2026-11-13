package com.foundation.authz.application;

import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.authz.infrastructure.RoleMutexRepository;
import com.foundation.authz.infrastructure.RoleRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 有效角色计算（F-05 BAS-05 §有效角色与互斥）。
 *
 * <pre>
 * 有效角色 = 直接授予(personId)
 *          ∪ 组织单元授予(personId 所在组织单元 ∈ 授权组织单元子树)
 *          ∪ 岗位授予(personId 当前岗位 ∈ 授权岗位)
 * 再过滤：生效期内，且不含与已选角色互斥的角色
 * </pre>
 *
 * <p>组织单元与岗位来自组织域（BAS-06），经 {@link SubjectMembership} 端口读取，
 * BAS-06 落地前为占位实现（两条来源均为空）。
 */
public class EffectiveRoleResolver {

    private final RoleGrantRepository grants;
    private final RoleMutexRepository mutexes;
    private final RoleRepository roles;
    private final SubjectMembership membership;
    private final AppIdResolver appIds;
    private final RoleMutexChecker mutexChecker;

    public EffectiveRoleResolver(RoleGrantRepository grants, RoleMutexRepository mutexes, RoleRepository roles,
                                 SubjectMembership membership, AppIdResolver appIds, RoleMutexChecker mutexChecker) {
        this.grants = grants;
        this.mutexes = mutexes;
        this.roles = roles;
        this.membership = membership;
        this.appIds = appIds;
        this.mutexChecker = mutexChecker;
    }

    /**
     * 计算人员在指定应用、指定时刻的有效角色。
     *
     * @param at 计算时刻；{@code null} 视为当前时刻
     */
    public Set<String> resolve(String personId, String appCode, Instant at) {
        Instant moment = at == null ? Instant.now() : at;
        Optional<String> appId = appIds.resolve(appCode);
        Set<String> personOrgUnits = membership.orgUnitsOf(personId);
        Set<String> personPosts = membership.postsOf(personId);

        Set<String> candidates = new LinkedHashSet<>();
        collectDirect(candidates, personId, moment);
        collectByOrgUnit(candidates, personOrgUnits, moment);
        collectByPost(candidates, personPosts, moment);

        Set<String> applicable = new LinkedHashSet<>();
        for (String roleCode : candidates) {
            roles.find(roleCode)
                    .filter(role -> availableIn(role, appId))
                    .ifPresent(role -> applicable.add(role.code()));
        }
        return mutexChecker.removeConflicts(applicable, mutexes.all());
    }

    private void collectDirect(Set<String> candidates, String personId, Instant moment) {
        for (RoleGrant grant : grants.list(GrantSubjectType.PERSON, personId)) {
            addIfEffective(candidates, grant, moment);
        }
    }

    private void collectByOrgUnit(Set<String> candidates, Set<String> personOrgUnits, Instant moment) {
        if (personOrgUnits.isEmpty()) {
            return;
        }
        for (RoleGrant grant : grants.list(GrantSubjectType.ORG_UNIT, null)) {
            Set<String> covered = grant.includeDescendants()
                    ? membership.descendantsOf(grant.subjectId())
                    : Set.of(grant.subjectId());
            if (personOrgUnits.stream().anyMatch(covered::contains)) {
                addIfEffective(candidates, grant, moment);
            }
        }
    }

    private void collectByPost(Set<String> candidates, Set<String> personPosts, Instant moment) {
        if (personPosts.isEmpty()) {
            return;
        }
        for (RoleGrant grant : grants.list(GrantSubjectType.POST, null)) {
            if (personPosts.contains(grant.subjectId())) {
                addIfEffective(candidates, grant, moment);
            }
        }
    }

    private static void addIfEffective(Set<String> candidates, RoleGrant grant, Instant moment) {
        if (grant.effectiveAt(moment)) {
            candidates.add(grant.roleCode());
        }
    }

    /** 全局角色跨应用生效；应用角色须属于该应用。 */
    private static boolean availableIn(Role role, Optional<String> appId) {
        return appId.isEmpty() || role.systemScoped() || appId.get().equals(role.appId());
    }
}
