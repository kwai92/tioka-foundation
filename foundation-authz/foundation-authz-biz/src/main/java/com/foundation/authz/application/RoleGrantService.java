package com.foundation.authz.application;

import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.api.RoleGrantView;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleMutex;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.authz.infrastructure.RoleMutexRepository;
import com.foundation.authz.infrastructure.RoleRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 授权维护与有效角色查询（F-05 BAS-05 §接口 {@code GET/POST/DELETE /api/grants}）。
 *
 * <p>硬规则：互斥角色不能授予同一对象；同一 {@code (role, subject)} 已有手工授权时，
 * {@code RULE}/{@code SYNC} 授权不得覆盖（只增不覆盖）。
 */
public class RoleGrantService implements GrantApi {

    private final RoleGrantRepository grants;
    private final RoleMutexRepository mutexes;
    private final RoleRepository roles;
    private final EffectiveRoleResolver resolver;
    private final RoleMutexChecker mutexChecker;

    public RoleGrantService(RoleGrantRepository grants, RoleMutexRepository mutexes, RoleRepository roles,
                            EffectiveRoleResolver resolver, RoleMutexChecker mutexChecker) {
        this.grants = grants;
        this.mutexes = mutexes;
        this.roles = roles;
        this.resolver = resolver;
        this.mutexChecker = mutexChecker;
    }

    @Override
    public List<RoleGrantView> list(String subjectType, String subjectId) {
        GrantSubjectType type = subjectType == null || subjectType.isBlank()
                ? null
                : GrantSubjectType.of(subjectType);
        return grants.list(type, subjectId).stream().map(RoleGrantService::toView).toList();
    }

    @Override
    @Transactional
    public String grant(RoleGrantCmd cmd) {
        String roleCode = cmd.roleCode();
        roles.find(roleCode).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在：" + roleCode));
        GrantSubjectType subjectType = GrantSubjectType.of(cmd.subjectType());
        GrantSource source = GrantSource.of(cmd.source());
        List<RoleGrant> existing = grants.list(subjectType, cmd.subjectId());

        boolean manualKept = existing.stream()
                .anyMatch(grant -> grant.roleCode().equals(roleCode) && grant.source() == GrantSource.MANUAL);
        if (manualKept && source != GrantSource.MANUAL) {
            throw new BizException(ErrorCode.CONFLICT, "手工授权不可被规则或同步覆盖：" + roleCode);
        }

        List<RoleMutex> mutexPairs = mutexes.all();
        for (RoleGrant grant : existing) {
            if (mutexChecker.conflict(roleCode, grant.roleCode(), mutexPairs)) {
                throw new BizException(ErrorCode.CONFLICT,
                        "互斥角色不能同时授予：" + roleCode + " / " + grant.roleCode());
            }
        }

        String id = Ids.newId(AuthzIdPrefix.GRANT);
        grants.insert(new RoleGrant(id, roleCode, subjectType, cmd.subjectId(), cmd.includeDescendants(),
                cmd.effectiveFrom(), cmd.effectiveTo(), source));
        return id;
    }

    @Override
    @Transactional
    public void revoke(String grantId, String operator) {
        grants.find(grantId).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "授权不存在：" + grantId));
        grants.delete(grantId);
    }

    @Override
    public Set<String> effectiveRoles(String personId, String appCode, Instant at) {
        return resolver.resolve(personId, appCode, at);
    }

    private static RoleGrantView toView(RoleGrant grant) {
        return new RoleGrantView(grant.id(), grant.roleCode(), grant.subjectType().name(), grant.subjectId(),
                grant.includeDescendants(), grant.effectiveFrom(), grant.effectiveTo(), grant.source().name());
    }
}
