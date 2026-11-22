package com.foundation.authz.application;

import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.config.AuthzProperties;
import com.foundation.authz.config.SeparationProperties;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.OrgAdminScope;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleMutex;
import com.foundation.authz.domain.RoleScope;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分级管理员越权拦截的口径单测（F-05 BAS-27 §分级授权，离线不依赖数据库）。
 *
 * <p>本类守的是**接线后的生产语义**：判定算法本身由既有的 {@code OrgAdminScopeServiceTest}（5 例）与
 * {@code OrgAdminScopeTest}（7 例）覆盖，本类只覆盖 {@link OrgAdminScopeGuard} 新增的三件事——
 * 豁免判定、目标组织单元解析（三种授权对象类型）、开关与审计。
 *
 * <p>刻意覆盖几个「错了就是权限多给」的边界：
 * <ul>
 *   <li><b>豁免</b>：未配范围者放行。这不只是宽松取舍——{@code assertGrantable} 在范围为空时会抛
 *       {@code FORBIDDEN}，无条件接入会立刻打挂全局 {@code system_admin} 的所有授权；</li>
 *   <li><b>PERSON 全量 AND</b>：主任职在范围内、兼职在范围外必须**拒绝**。因为
 *       {@code EffectiveRoleResolver.collectByOrgUnit} 会让被授权人在其所有挂靠组织获得角色，
 *       只校验主任职等于留一条「借兼职影响外区」的越权通道；</li>
 *   <li><b>空目标 fail-closed</b>：目标组织单元解析为空集时必须**拒绝**。此处不能按
 *       「空集的全称量化为真」放行——那是一次 vacuous pass：{@code PERSON} 型授权经
 *       {@code collectByPerson} 直接对该人生效（不依赖组织单元），{@code POST} 型经
 *       {@code collectByPost} 对该岗位下所有人生效，两者都真实生效；放行即等于允许分级管理员
 *       通过「给无任职记录的人预授权」或「给未归属组织单元的岗位授权」越权。</li>
 * </ul>
 */
class OrgAdminScopeGuardTest {

    private static final String ADMIN = "admin_1";
    private static final String ROLE = "r.hr";
    /** 不在任何白名单里的角色码，专供「白名单外拒绝」用例使用。 */
    private static final String OUT_OF_WHITELIST = "r.finance";
    private static final String MUTEX_ROLE = "audit_admin";
    private static final String ORG_EAST = "org_east";
    private static final String ORG_EAST_CHILD = "org_east_child";
    private static final String ORG_NORTH = "org_north";

    private final TestDoubles.InMemoryOrgAdminScopeRepository scopes =
            new TestDoubles.InMemoryOrgAdminScopeRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();
    private final TestDoubles.StubAuditApi audit = new TestDoubles.StubAuditApi();
    private final AuthzProperties properties = new AuthzProperties();

    private OrgAdminScopeGuard guard;

    @BeforeEach
    void setUp() {
        guard = new OrgAdminScopeGuard(new OrgAdminScopeService(scopes, membership), properties, membership,
                new AuthzAuditWriter(TestDoubles.provider(audit)));
    }

    // ---------------------------------------------------------------- 豁免

    @Test
    void 未配范围者不受约束() {
        // 全局 system_admin / app_admin 从未配 fdn_org_admin_scope。
        // 若这里不放行，assertGrantable 会因 scopes.list() 为空而抛 FORBIDDEN，
        // 打挂所有现有授权流程（含 RoleGrantServiceTest / SeparationHighRiskReviewTest 的全部用例）。
        assertThatCode(() -> guard.check(cmd(ADMIN, GrantSubjectType.ORG_UNIT, ORG_NORTH), GrantSubjectType.ORG_UNIT))
                .doesNotThrowAnyException();
        assertThat(audit.actions()).isEmpty();
    }

    @Test
    void 操作人为空或空白时视为未配范围() {
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));

        // 内部调用可能没有操作人上下文；此时不存在「分级管理员」身份
        assertThatCode(() -> guard.check(cmd(null, GrantSubjectType.ORG_UNIT, ORG_NORTH), GrantSubjectType.ORG_UNIT))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.check(cmd("  ", GrantSubjectType.ORG_UNIT, ORG_NORTH), GrantSubjectType.ORG_UNIT))
                .doesNotThrowAnyException();
        assertThat(audit.actions()).isEmpty();
    }

    @Test
    void 部署开关关闭时放行并留痕() {
        scopeOf(ADMIN, ORG_EAST, false, List.of(ROLE));
        properties.setOrgAdminScopeEnforced(false);

        // 范围外 + 白名单外，开关关闭后仍放行（与 RoleMutexGuard 的 separation.enabled 同惯例）
        assertThatCode(() -> guard.check(cmd(ADMIN, GrantSubjectType.ORG_UNIT, ORG_NORTH), GrantSubjectType.ORG_UNIT))
                .doesNotThrowAnyException();
        assertThat(audit.actions()).contains("authz.org_admin_scope_switch_off");
    }

    @Test
    void 开关默认开启() {
        // 生产依赖「默认即受约束」：若默认关闭，接线等于没接（BAS-27 验收口径继续不成立），
        // 而所有测试仍可全绿——属静默失效，故把默认值本身钉成断言
        assertThat(new AuthzProperties().isOrgAdminScopeEnforced())
                .as("foundation.authz.org-admin-scope-enforced 默认值")
                .isTrue();
    }

    // ---------------------------------------------------------------- ORG_UNIT 型

    @Test
    void 组织单元型在范围内放行() {
        scopeOf(ADMIN, ORG_EAST, false, List.of());

        assertThatCode(() -> guard.check(cmd(ADMIN, GrantSubjectType.ORG_UNIT, ORG_EAST), GrantSubjectType.ORG_UNIT))
                .doesNotThrowAnyException();
        assertThat(audit.actions()).isEmpty();
    }

    @Test
    void 组织单元型含下级时子树内放行() {
        scopeOf(ADMIN, ORG_EAST, true, List.of());
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST, ORG_EAST_CHILD));

        assertThatCode(() -> guard.check(
                cmd(ADMIN, GrantSubjectType.ORG_UNIT, ORG_EAST_CHILD), GrantSubjectType.ORG_UNIT))
                .doesNotThrowAnyException();
    }

    @Test
    void 组织单元型不含下级时子树内也拒绝() {
        scopeOf(ADMIN, ORG_EAST, false, List.of());
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST, ORG_EAST_CHILD));

        assertForbidden(cmd(ADMIN, GrantSubjectType.ORG_UNIT, ORG_EAST_CHILD), GrantSubjectType.ORG_UNIT);
    }

    @Test
    void 组织单元型范围外拒绝并留痕() {
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST, ORG_EAST_CHILD));

        assertForbidden(cmd(ADMIN, GrantSubjectType.ORG_UNIT, ORG_NORTH), GrantSubjectType.ORG_UNIT);
        assertThat(audit.actions()).contains("authz.org_admin_scope_denied");
    }

    @Test
    void 角色不在白名单时即使在范围内也拒绝() {
        // 白名单只有 r.hr，本次授予 OUT_OF_WHITELIST → 拒绝。
        // 注意本用例必须用**不同的角色码**：cmd(...) 默认授予 ROLE，而 ROLE 正在白名单里，
        // 那样断言的就是「白名单内的角色被拒」，与用例名相反（首版即因此写错而失败）。
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST));

        assertForbidden(cmd(ADMIN, OUT_OF_WHITELIST, GrantSubjectType.ORG_UNIT, ORG_EAST),
                GrantSubjectType.ORG_UNIT);
        assertThat(audit.actions()).contains("authz.org_admin_scope_denied");
    }

    @Test
    void 白名单为空表示该范围内角色不限() {
        scopeOf(ADMIN, ORG_EAST, true, List.of());
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST));

        assertThatCode(() -> guard.check(cmd(ADMIN, GrantSubjectType.ORG_UNIT, ORG_EAST), GrantSubjectType.ORG_UNIT))
                .doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- PERSON 型

    @Test
    void 人员型全部未结束任职都在范围内时放行() {
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST, ORG_EAST_CHILD));
        membership.orgUnits.put("per_1", Set.of(ORG_EAST, ORG_EAST_CHILD));

        assertThatCode(() -> guard.check(cmd(ADMIN, GrantSubjectType.PERSON, "per_1"), GrantSubjectType.PERSON))
                .doesNotThrowAnyException();
    }

    @Test
    void 人员型兼职区超出范围时拒绝() {
        // 全量 AND：主任职在华东（范围内）、兼职在华北（范围外）→ 拒绝。
        // 只校验主任职会留下越权通道：collectByOrgUnit 让该人在**所有**挂靠组织获得角色，
        // 于是华东区管理员可借跨区兼职者影响华北区。
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST, ORG_EAST_CHILD));
        membership.orgUnits.put("per_1", Set.of(ORG_EAST, ORG_NORTH));

        assertForbidden(cmd(ADMIN, GrantSubjectType.PERSON, "per_1"), GrantSubjectType.PERSON);
        assertThat(audit.actions()).contains("authz.org_admin_scope_denied");
    }

    @Test
    void 人员型解析不出组织单元时拒绝而非放行() {
        // fail-closed：无任职记录的人。此处**不得**按「空集全称量化为真」放行——
        // PERSON 型授权经 collectByPerson 直接对该人生效（不依赖任何组织单元），
        // 放行即允许「给无任职者预授权」越权。
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));
        membership.orgUnits.put("per_ghost", Set.of());

        assertForbidden(cmd(ADMIN, GrantSubjectType.PERSON, "per_ghost"), GrantSubjectType.PERSON);
    }

    // ---------------------------------------------------------------- POST 型

    @Test
    void 岗位型经端口解析所属组织单元后判定() {
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST, ORG_EAST_CHILD));
        membership.postOrgUnits.put("pos_1", Set.of(ORG_EAST_CHILD));

        assertThatCode(() -> guard.check(cmd(ADMIN, GrantSubjectType.POST, "pos_1"), GrantSubjectType.POST))
                .doesNotThrowAnyException();
    }

    @Test
    void 岗位型所属组织单元超出范围时拒绝() {
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));
        membership.descendants.put(ORG_EAST, Set.of(ORG_EAST, ORG_EAST_CHILD));
        membership.postOrgUnits.put("pos_1", Set.of(ORG_NORTH));

        assertForbidden(cmd(ADMIN, GrantSubjectType.POST, "pos_1"), GrantSubjectType.POST);
    }

    @Test
    void 岗位型未归属组织单元时拒绝而非放行() {
        // fail-closed：占位实现 NoopSubjectMembership 的 orgUnitsOfPost 恒返回空集，
        // 故本条同时钉住「半接线不会静默放宽」——真库实现缺失时 POST 型授权对分级管理员一律拒绝，
        // 而不是悄悄放行。
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));

        assertForbidden(cmd(ADMIN, GrantSubjectType.POST, "pos_unassigned"), GrantSubjectType.POST);
    }

    @Test
    void 授权对象ID为空白时拒绝() {
        scopeOf(ADMIN, ORG_EAST, true, List.of(ROLE));

        assertForbidden(cmd(ADMIN, GrantSubjectType.ORG_UNIT, "  "), GrantSubjectType.ORG_UNIT);
        assertForbidden(cmd(ADMIN, GrantSubjectType.PERSON, null), GrantSubjectType.PERSON);
    }

    // ---------------------------------------------------------------- 端到端接线

    @Test
    void 越权时授权不落库且拒绝优先于互斥与复核() {
        // 证明接线真的生效（而非只加了个没人调的类）。
        // 造数刻意让互斥分支也**可触发**（目标已持有与 ROLE 互斥的 audit_admin），
        // 于是「拿到 FORBIDDEN 而非 CONFLICT」才真正鉴别出范围校验排在互斥之前——
        // 若把 scopeGuard.check 挪到 mutexGuard.check 之后，本用例会以 CONFLICT 失败。
        TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
        TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
        TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
        TestDoubles.InMemoryHighRiskReviewRepository reviews = new TestDoubles.InMemoryHighRiskReviewRepository();
        TestDoubles.InMemoryPermissionCodeRepository permissionCodes =
                new TestDoubles.InMemoryPermissionCodeRepository();
        TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
        apps.register("app_a", "app_a");
        AuthzAuditWriter auditWriter = new AuthzAuditWriter(TestDoubles.provider(audit));
        SeparationProperties separation = new SeparationProperties();

        roles.insert(new Role(ROLE, ROLE, RoleScope.APP, "app_a", false, null, Set.of()));
        roles.insert(new Role(MUTEX_ROLE, MUTEX_ROLE, RoleScope.APP, "app_a", false, null, Set.of()));
        mutexes.pairs.add(new RoleMutex(ROLE, MUTEX_ROLE));
        // 目标组织单元已持有互斥角色 → 互斥分支可触发
        grants.insert(new RoleGrant("grt_seed", MUTEX_ROLE, GrantSubjectType.ORG_UNIT, ORG_NORTH, false,
                null, null, GrantSource.MANUAL));

        scopeOf(ADMIN, ORG_EAST, false, List.of(ROLE)); // 范围只含华东，不含 ORG_NORTH

        RoleGrantService service = new RoleGrantService(grants, roles,
                new EffectiveRoleResolver(grants, mutexes, roles, membership,
                        new AppIdResolver(apps), new RoleMutexChecker()),
                new RoleMutexGuard(mutexes, new RoleMutexChecker(), separation, auditWriter),
                new HighRiskGrantReviewService(reviews, grants, permissionCodes, separation, auditWriter),
                guard);

        RoleGrantCmd outOfScope = new RoleGrantCmd(ROLE, GrantSubjectType.ORG_UNIT.name(), ORG_NORTH, false,
                null, null, GrantSource.MANUAL.name(), ADMIN);
        assertThatThrownBy(() -> service.grant(outOfScope))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .as("范围校验必须排在互斥之前，故拿到 FORBIDDEN 而非 CONFLICT")
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(grants.list(GrantSubjectType.ORG_UNIT, ORG_NORTH))
                .as("越权授予不得落库")
                .extracting(RoleGrant::roleCode)
                .containsExactly(MUTEX_ROLE); // 只剩造数时预置的那条
        assertThat(audit.actions()).contains("authz.org_admin_scope_denied");
        assertThat(audit.actions()).doesNotContain("authz.role.mutex_denied");
    }

    @Test
    void 范围内的授权照常落库() {
        // 反向对照：接线不得把合法授权也一并拦掉（否则等于「为了安全把功能关掉」）
        TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
        TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
        TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
        TestDoubles.InMemoryHighRiskReviewRepository highRiskReviews =
                new TestDoubles.InMemoryHighRiskReviewRepository();
        TestDoubles.InMemoryPermissionCodeRepository permissionCodes =
                new TestDoubles.InMemoryPermissionCodeRepository();
        TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
        apps.register("app_a", "app_a");
        AuthzAuditWriter auditWriter = new AuthzAuditWriter(TestDoubles.provider(audit));
        SeparationProperties separation = new SeparationProperties();

        roles.insert(new Role(ROLE, ROLE, RoleScope.APP, "app_a", false, null, Set.of()));
        scopeOf(ADMIN, ORG_EAST, false, List.of(ROLE));

        RoleGrantService service = new RoleGrantService(grants, roles,
                new EffectiveRoleResolver(grants, mutexes, roles, membership,
                        new AppIdResolver(apps), new RoleMutexChecker()),
                new RoleMutexGuard(mutexes, new RoleMutexChecker(), separation, auditWriter),
                new HighRiskGrantReviewService(highRiskReviews, grants, permissionCodes, separation, auditWriter),
                guard);

        service.grant(new RoleGrantCmd(ROLE, GrantSubjectType.ORG_UNIT.name(), ORG_EAST, false,
                null, null, GrantSource.MANUAL.name(), ADMIN));

        assertThat(grants.list(GrantSubjectType.ORG_UNIT, ORG_EAST))
                .extracting(RoleGrant::roleCode)
                .containsExactly(ROLE);
        assertThat(audit.actions()).doesNotContain("authz.org_admin_scope_denied");
    }

    // ---------------------------------------------------------------- 造数

    private void scopeOf(String userId, String orgUnitId, boolean includeDescendants, List<String> grantableRoles) {
        scopes.insert(new OrgAdminScope("oas_" + userId + "_" + orgUnitId, userId, orgUnitId,
                includeDescendants, grantableRoles));
    }

    private static RoleGrantCmd cmd(String operator, GrantSubjectType subjectType, String subjectId) {
        return cmd(operator, ROLE, subjectType, subjectId);
    }

    private static RoleGrantCmd cmd(String operator, String roleCode, GrantSubjectType subjectType,
                                    String subjectId) {
        return new RoleGrantCmd(roleCode, subjectType.name(), subjectId, false, null, null,
                GrantSource.MANUAL.name(), operator);
    }

    private void assertForbidden(RoleGrantCmd cmd, GrantSubjectType subjectType) {
        assertThatThrownBy(() -> guard.check(cmd, subjectType))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
