package com.foundation.authz.application;

import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.api.RoleGrantView;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 授权硬规则：只增不覆盖与互斥（F-05 BAS-05 §接口 {@code /api/grants}）。 */
class RoleGrantServiceTest {

    private static final String PERSON = "user_1";

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
    private final TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
    private final TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();
    private final RoleMutexChecker mutexChecker = new RoleMutexChecker();

    private RoleGrantService service;

    @BeforeEach
    void setUp() {
        apps.register("app_a", "app_a");
        EffectiveRoleResolver resolver = new EffectiveRoleResolver(grants, mutexes, roles,
                membership, new AppIdResolver(apps), mutexChecker);
        service = new RoleGrantService(grants, mutexes, roles, resolver, mutexChecker);
    }

    @Test
    void manualGrantCannotBeOverriddenByRuleOrSync() {
        roles.insert(role("r.a"));
        grants.insert(new RoleGrant("grt_1", "r.a", GrantSubjectType.PERSON, PERSON, false,
                null, null, GrantSource.MANUAL));

        assertThatThrownBy(() -> service.grant(cmd("r.a", GrantSource.RULE.name())))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> service.grant(cmd("r.a", GrantSource.SYNC.name())))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void mutuallyExclusiveRoleCannotBeGrantedToSameSubject() {
        roles.insert(role("r.admin"));
        roles.insert(role("r.auditor"));
        mutexes.pairs.add(new RoleMutex("r.admin", "r.auditor"));
        service.grant(cmd("r.admin", GrantSource.MANUAL.name()));

        assertThatThrownBy(() -> service.grant(cmd("r.auditor", GrantSource.MANUAL.name())))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void grantPersistsAndIsListedBack() {
        roles.insert(role("r.a"));

        String id = service.grant(cmd("r.a", GrantSource.MANUAL.name()));

        List<RoleGrantView> views = service.list(GrantSubjectType.PERSON.name(), PERSON);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(id);
        assertThat(views.get(0).roleCode()).isEqualTo("r.a");
    }

    private static Role role(String code) {
        return new Role(code, code, RoleScope.APP, "app_a", false, null, Set.of());
    }

    private static RoleGrantCmd cmd(String roleCode, String source) {
        return new RoleGrantCmd(roleCode, GrantSubjectType.PERSON.name(), PERSON, false, null, null, source,
                "tester");
    }
}
