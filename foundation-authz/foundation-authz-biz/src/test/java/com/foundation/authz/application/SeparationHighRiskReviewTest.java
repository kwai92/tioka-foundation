package com.foundation.authz.application;

import com.foundation.authz.api.HighRiskReviewPageView;
import com.foundation.authz.api.HighRiskReviewView;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.config.AuthzProperties;
import com.foundation.authz.config.SeparationProperties;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.ReviewStatus;
import com.foundation.authz.domain.RiskLevel;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleMutex;
import com.foundation.authz.domain.RoleScope;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 三员分立与高风险授权复核（F-09 BAS-19 §规则）：
 * 互斥授予拒绝 + 审计、部署开关放开、高风险授权「通过前不生效、拒绝永不生效」。
 */
class SeparationHighRiskReviewTest {

    private static final String PERSON = "user_1";
    private static final String HIGH = "fdn:hrgrant:review";

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
    private final TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
    private final TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
    private final TestDoubles.InMemoryPermissionCodeRepository permissionCodes =
            new TestDoubles.InMemoryPermissionCodeRepository();
    private final TestDoubles.InMemoryHighRiskReviewRepository reviews =
            new TestDoubles.InMemoryHighRiskReviewRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();
    private final TestDoubles.StubAuditApi audit = new TestDoubles.StubAuditApi();
    private final RoleMutexChecker checker = new RoleMutexChecker();

    private final SeparationProperties separation = new SeparationProperties();
    private final AuthzAuditWriter auditWriter = new AuthzAuditWriter(TestDoubles.provider(audit));
    private final RoleMutexGuard mutexGuard = new RoleMutexGuard(mutexes, checker, separation, auditWriter);
    private final HighRiskGrantReviewService reviewService = new HighRiskGrantReviewService(
            reviews, grants, permissionCodes, separation, auditWriter);
    /**
     * 分级管理员拦截（BAS-27）：本测试的范围仓储为空，即操作人「未配范围」→ guard 直接放行，
     * 故下列断言只覆盖互斥与高风险复核，**不覆盖** guard 自身行为
     * （后者由 {@code OrgAdminScopeGuardTest} 覆盖）。此处存在只为满足构造签名。
     */
    private final OrgAdminScopeGuard scopeGuard = new OrgAdminScopeGuard(
            new OrgAdminScopeService(new TestDoubles.InMemoryOrgAdminScopeRepository(), membership),
            new AuthzProperties(), membership, auditWriter);

    private RoleGrantService grantsApi;

    @BeforeEach
    void setUp() {
        apps.register("app_a", "app_a");
        EffectiveRoleResolver resolver = new EffectiveRoleResolver(grants, mutexes, roles, membership,
                new AppIdResolver(apps), checker);
        grantsApi = new RoleGrantService(grants, roles, resolver, mutexGuard, reviewService, scopeGuard);
    }

    @Test
    void mutuallyExclusiveGrantIsDeniedAndAudited() {
        roles.insert(role("system_admin"));
        roles.insert(role("audit_admin"));
        mutexes.pairs.add(new RoleMutex("system_admin", "audit_admin"));
        grantsApi.grant(cmd("system_admin"));

        assertThatThrownBy(() -> grantsApi.grant(cmd("audit_admin")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(audit.actions()).contains("authz.role.mutex_denied");
    }

    @Test
    void mutexConstraintCanBeDisabledByDeploymentSwitch() {
        separation.setEnabled(false);
        roles.insert(role("system_admin"));
        roles.insert(role("audit_admin"));
        mutexes.pairs.add(new RoleMutex("system_admin", "audit_admin"));
        grantsApi.grant(cmd("system_admin"));

        grantsApi.grant(cmd("audit_admin"));

        assertThat(grants.list(GrantSubjectType.PERSON, PERSON)).hasSize(2);
        assertThat(audit.actions()).contains("authz.role.mutex_switch_off");
    }

    @Test
    void highRiskGrantTakesEffectOnlyAfterApproval() {
        permissionCodes.insert(highRiskCode(HIGH));
        roles.insert(roleWithPermissions("security_admin", HIGH));

        String grantId = grantsApi.grant(cmd("security_admin"));

        // 复核已登记，但授权未落库（通过前不生效）
        assertThat(grants.list(GrantSubjectType.PERSON, PERSON)).isEmpty();
        HighRiskReviewPageView pending = reviewService.page(ReviewStatus.PENDING.name(), null, 20);
        assertThat(pending.items()).hasSize(1);
        assertThat(pending.items().get(0).grantId()).isEqualTo(grantId);
        assertThat(pending.items().get(0).status()).isEqualTo("PENDING");
        assertThat(audit.actions()).contains("authz.hrgrant.pending");

        HighRiskReviewView approved = reviewService.approve(pending.items().get(0).id(), "reviewer", "允许");

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(grants.find(grantId)).isPresent();
        assertThat(audit.actions()).contains("authz.hrgrant.approved");
    }

    @Test
    void rejectedHighRiskGrantNeverTakesEffect() {
        permissionCodes.insert(highRiskCode(HIGH));
        roles.insert(roleWithPermissions("security_admin", HIGH));

        String grantId = grantsApi.grant(cmd("security_admin"));
        String reviewId = reviewService.page(ReviewStatus.PENDING.name(), null, 20).items().get(0).id();

        HighRiskReviewView rejected = reviewService.reject(reviewId, "reviewer", "风险过高");

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(grants.find(grantId)).isEmpty();
        assertThat(audit.actions()).contains("authz.hrgrant.rejected");
    }

    @Test
    void highRiskReviewCanBeDisabledByDeploymentSwitch() {
        separation.setHighRiskReview(false);
        permissionCodes.insert(highRiskCode(HIGH));
        roles.insert(roleWithPermissions("security_admin", HIGH));

        grantsApi.grant(cmd("security_admin"));

        assertThat(grants.list(GrantSubjectType.PERSON, PERSON)).hasSize(1);
        assertThat(reviews.byId).isEmpty();
    }

    @Test
    void approveRequiresPendingReview() {
        assertThatThrownBy(() -> reviewService.approve("hrr_missing", "u", "r"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        permissionCodes.insert(highRiskCode(HIGH));
        roles.insert(roleWithPermissions("security_admin", HIGH));
        grantsApi.grant(cmd("security_admin"));
        String reviewId = reviewService.page(null, null, 20).items().get(0).id();
        reviewService.approve(reviewId, "u", "ok");

        assertThatThrownBy(() -> reviewService.approve(reviewId, "u", "again"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    private static Role role(String code) {
        return new Role(code, code, RoleScope.SYSTEM, null, true, null, Set.of());
    }

    private static Role roleWithPermissions(String code, String... permissions) {
        return new Role(code, code, RoleScope.SYSTEM, null, true, null, Set.of(permissions));
    }

    private static RoleGrantCmd cmd(String roleCode) {
        return new RoleGrantCmd(roleCode, GrantSubjectType.PERSON.name(), PERSON, false, null, null,
                GrantSource.MANUAL.name(), "tester");
    }

    private static PermissionCode highRiskCode(String code) {
        return new PermissionCode("pms_" + code.replace(':', '_'), "app_a", code, code, RiskLevel.HIGH,
                LifecycleStatus.ACTIVE, Instant.now(), null);
    }
}
