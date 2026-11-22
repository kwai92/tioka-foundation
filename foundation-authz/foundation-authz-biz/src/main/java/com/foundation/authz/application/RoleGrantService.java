package com.foundation.authz.application;

import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.api.RoleGrantView;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.infrastructure.RoleGrantRepository;
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
 * <p>硬规则：分级管理员不能越权授予（BAS-27 {@link OrgAdminScopeGuard}）；互斥角色不能授予同一对象
 * （BAS-19 {@link RoleMutexGuard}）；同一 {@code (role, subject)} 已有手工授权时 {@code RULE}/{@code SYNC}
 * 授权不得覆盖（只增不覆盖）；含高风险权限码的授权须安全管理员复核后生效（BAS-19
 * {@link HighRiskGrantReviewService}）。
 *
 * <p>校验顺序即拒绝优先级：先判操作人**有没有资格**授（范围），再判这次授予**合不合规**
 * （覆盖保护 → 互斥 → 高风险复核）。故越权者拿到的是 {@code FORBIDDEN} 而不是 {@code CONFLICT}。
 */
public class RoleGrantService implements GrantApi {

    private final RoleGrantRepository grants;
    private final RoleRepository roles;
    private final EffectiveRoleResolver resolver;
    private final RoleMutexGuard mutexGuard;
    private final HighRiskGrantReviewService reviews;
    private final OrgAdminScopeGuard scopeGuard;

    public RoleGrantService(RoleGrantRepository grants, RoleRepository roles, EffectiveRoleResolver resolver,
                            RoleMutexGuard mutexGuard, HighRiskGrantReviewService reviews,
                            OrgAdminScopeGuard scopeGuard) {
        this.grants = grants;
        this.roles = roles;
        this.resolver = resolver;
        this.mutexGuard = mutexGuard;
        this.reviews = reviews;
        this.scopeGuard = scopeGuard;
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
        Role role = roles.find(roleCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在：" + roleCode));
        GrantSubjectType subjectType = GrantSubjectType.of(cmd.subjectType());
        GrantSource source = GrantSource.of(cmd.source());

        // 分级管理员越权拦截（BAS-27）：已配范围者只能授范围内的组织单元与角色白名单
        scopeGuard.check(cmd, subjectType);

        List<RoleGrant> existing = grants.list(subjectType, cmd.subjectId());

        boolean manualKept = existing.stream()
                .anyMatch(grant -> grant.roleCode().equals(roleCode) && grant.source() == GrantSource.MANUAL);
        if (manualKept && source != GrantSource.MANUAL) {
            throw new BizException(ErrorCode.CONFLICT, "手工授权不可被规则或同步覆盖：" + roleCode);
        }

        // 三员互斥（BAS-19）：命中互斥对则拒绝（CONFLICT）并写审计；部署开关可关闭
        mutexGuard.check(roleCode, subjectType, cmd.subjectId(), existing, cmd.operator());

        String id = Ids.newId(AuthzIdPrefix.GRANT);
        RoleGrant grant = new RoleGrant(id, roleCode, subjectType, cmd.subjectId(), cmd.includeDescendants(),
                cmd.effectiveFrom(), cmd.effectiveTo(), source);

        // 高风险授权复核（BAS-19）：含 HIGH 权限码时先登记待复核（授权暂不落库、暂不生效）
        if (reviews.deferIfHighRisk(grant, role, cmd.operator())) {
            return id;
        }
        grants.insert(grant);
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
