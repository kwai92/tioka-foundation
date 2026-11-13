package com.foundation.authz.application;

import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleMutex;
import com.foundation.authz.domain.RoleScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 有效角色计算（F-05 BAS-05 §测试 {@code EffectiveRoleResolverTest}）。 */
class EffectiveRoleResolverTest {

    private static final String APP_A = "app_a";
    private static final String APP_B = "app_b";
    private static final String PERSON = "user_1";
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
    private final TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
    private final TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();
    private final RoleMutexChecker mutexChecker = new RoleMutexChecker();

    private EffectiveRoleResolver resolver;

    @BeforeEach
    void setUp() {
        apps.register(APP_A, APP_A);
        apps.register(APP_B, APP_B);
        resolver = new EffectiveRoleResolver(grants, mutexes, roles,
                membership, new AppIdResolver(apps), mutexChecker);
    }

    @Test
    void directOrgUnitAndPostGrantsAreUnioned() {
        register(role("r.direct"));
        register(role("r.org"));
        register(role("r.post"));
        grant("r.direct", GrantSubjectType.PERSON, PERSON, false);
        grant("r.org", GrantSubjectType.ORG_UNIT, "org_1", true);
        grant("r.post", GrantSubjectType.POST, "post_1", false);
        membership.orgUnits.put(PERSON, Set.of("org_1"));
        membership.posts.put(PERSON, Set.of("post_1"));

        assertThat(resolver.resolve(PERSON, APP_A, NOW)).containsExactlyInAnyOrder("r.direct", "r.org", "r.post");
    }

    @Test
    void orgUnitGrantCoversDescendants() {
        register(role("r.org"));
        grant("r.org", GrantSubjectType.ORG_UNIT, "org_1", true);
        membership.orgUnits.put(PERSON, Set.of("org_2"));
        membership.descendants.put("org_1", Set.of("org_1", "org_2"));

        assertThat(resolver.resolve(PERSON, APP_A, NOW)).containsExactly("r.org");
    }

    @Test
    void expiredGrantIsFiltered() {
        register(role("r.expired"));
        grants.insert(new RoleGrant("grt_1", "r.expired", GrantSubjectType.PERSON, PERSON, false,
                null, Instant.parse("2026-01-01T00:00:00Z"), GrantSource.MANUAL));

        assertThat(resolver.resolve(PERSON, APP_A, NOW)).isEmpty();
    }

    @Test
    void mutuallyExclusiveRoleIsDropped() {
        register(role("r.admin"));
        register(role("r.auditor"));
        grant("r.admin", GrantSubjectType.PERSON, PERSON, false);
        grant("r.auditor", GrantSubjectType.PERSON, PERSON, false);
        mutexes.pairs.add(new RoleMutex("r.admin", "r.auditor"));

        assertThat(resolver.resolve(PERSON, APP_A, NOW)).containsExactly("r.admin");
    }

    @Test
    void appScopedRoleIsFilteredByApplicationButSystemRoleIsNot() {
        roles.insert(new Role("r.app", "应用角色", RoleScope.APP, APP_A, false, null, Set.of("app:read")));
        roles.insert(new Role("r.sys", "全局角色", RoleScope.SYSTEM, null, false, null, Set.of("fdn:read")));
        grant("r.app", GrantSubjectType.PERSON, PERSON, false);
        grant("r.sys", GrantSubjectType.PERSON, PERSON, false);

        assertThat(resolver.resolve(PERSON, APP_A, NOW)).containsExactlyInAnyOrder("r.app", "r.sys");
        assertThat(resolver.resolve(PERSON, APP_B, NOW)).containsExactly("r.sys");
    }

    private void register(Role role) {
        roles.insert(role);
    }

    private static Role role(String code) {
        return new Role(code, code, RoleScope.APP, APP_A, false, null, Set.of());
    }

    private void grant(String roleCode, GrantSubjectType subjectType, String subjectId, boolean descendants) {
        grants.insert(new RoleGrant("grt_" + roleCode, roleCode, subjectType, subjectId, descendants,
                null, null, GrantSource.MANUAL));
    }
}
