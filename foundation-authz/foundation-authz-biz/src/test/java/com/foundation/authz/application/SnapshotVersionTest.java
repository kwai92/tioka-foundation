package com.foundation.authz.application;

import com.foundation.authz.api.AuthzChangedEvent;
import com.foundation.authz.api.AuthzSnapshotView;
import com.foundation.authz.api.SnapshotRecomputeCmd;
import com.foundation.authz.api.SnapshotRecomputeResult;
import com.foundation.authz.domain.AffectedPersonResolver;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleScope;
import com.foundation.authz.infrastructure.AuthzChangedEventPublisher;
import com.foundation.common.api.event.EventProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 授权快照版本递增与变更事件（F-06 BAS-07 §失效与事件）。 */
class SnapshotVersionTest {

    private static final String PERSON = "user_1";
    private static final String OTHER = "user_2";
    private static final String APP = "app_a";

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
    private final TestDoubles.InMemoryRoleGrantRepository grants = new TestDoubles.InMemoryRoleGrantRepository();
    private final TestDoubles.InMemoryRoleMutexRepository mutexes = new TestDoubles.InMemoryRoleMutexRepository();
    private final TestDoubles.InMemoryPermissionCodeRepository permissionCodes =
            new TestDoubles.InMemoryPermissionCodeRepository();
    private final TestDoubles.InMemoryMenuRepository menus = new TestDoubles.InMemoryMenuRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();
    private final TestDoubles.StubDataScopeApi dataScopes = new TestDoubles.StubDataScopeApi();
    private final TestDoubles.InMemorySnapshotRepository snapshots = new TestDoubles.InMemorySnapshotRepository();
    private final TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();

    private SnapshotRecomputeService recomputeService;
    private SnapshotAppService appService;

    @BeforeEach
    void setUp() {
        apps.register("app_a", APP);
        roles.insert(new Role("r.a", "r.a", RoleScope.APP, "app_a", false, null, Set.of("a:read")));
        grants.insert(new RoleGrant("grt_1", "r.a", GrantSubjectType.PERSON, PERSON, false, null, null,
                GrantSource.MANUAL));
        permissionCodes.insert(new PermissionCode("pms_1", "app_a", "a:read", "a:read", null,
                LifecycleStatus.ACTIVE, null, null));

        AppIdResolver appIds = new AppIdResolver(apps);
        EffectiveRoleResolver effectiveRoles = new EffectiveRoleResolver(grants, mutexes, roles, membership,
                appIds, new RoleMutexChecker());
        FeatureToggleGate toggles = new FeatureToggleGate(new TestDoubles.StubFeatureToggleApi(),
                new TestDoubles.InMemorySubsystemRepository(), new TestDoubles.InMemoryModuleRepository(),
                new TestDoubles.InMemoryFunctionRepository(), menus);
        AuthorizationQueryService authorizations = new AuthorizationQueryService(effectiveRoles, roles,
                permissionCodes, appIds, toggles);
        SnapshotAssembler assembler = new SnapshotAssembler(effectiveRoles, authorizations, menus, dataScopes,
                membership, appIds, toggles);

        AuthzChangedEventPublisher publisher = new AuthzChangedEventPublisher(outbox, new EventProperties());
        recomputeService = new SnapshotRecomputeService(snapshots, assembler, publisher,
                new AffectedPersonResolver(membership));
        appService = new SnapshotAppService(snapshots, recomputeService);
    }

    @Test
    void versionIncrementsMonotonicallyOnEachRecompute() {
        assertThat(recomputeService.recompute(PERSON, APP).version()).isEqualTo(1);
        assertThat(recomputeService.recompute(PERSON, APP).version()).isEqualTo(2);
        assertThat(recomputeService.recompute(PERSON, APP).version()).isEqualTo(3);

        assertThat(snapshots.version(PERSON, APP)).contains(3L);
    }

    @Test
    void publishesChangedEventPerRecompute() {
        recomputeService.recompute(PERSON, APP);
        recomputeService.recompute(PERSON, APP);

        assertThat(outbox.eventTypes).containsExactly(AuthzChangedEvent.EVENT_TYPE, AuthzChangedEvent.EVENT_TYPE);
        assertThat(outbox.payloads.get(1)).containsEntry("snapshotVersion", 2L)
                .containsEntry("changeType", AuthzChangedEvent.CHANGE_TYPE_RECOMPUTED)
                .containsEntry("personId", PERSON)
                .containsEntry("appCode", APP);
    }

    @Test
    void findReturnsStoredSnapshotWithoutRepublishing() {
        recomputeService.recompute(PERSON, APP);
        int published = outbox.eventTypes.size();

        AuthzSnapshotView view = appService.find(APP, PERSON).orElseThrow();

        assertThat(view.version()).isEqualTo(1);
        assertThat(view.permissions()).containsExactly("a:read");
        assertThat(outbox.eventTypes).hasSize(published);
    }

    @Test
    void findRecomputesWhenSnapshotMissing() {
        AuthzSnapshotView view = appService.find(APP, PERSON).orElseThrow();

        assertThat(view.version()).isEqualTo(1);
        assertThat(outbox.eventTypes).containsExactly(AuthzChangedEvent.EVENT_TYPE);
    }

    @Test
    void findIfNewerReturnsEmptyWhenVersionMatches() {
        recomputeService.recompute(PERSON, APP);

        assertThat(appService.findIfNewer(APP, PERSON, 1)).isEmpty();
        assertThat(appService.findIfNewer(APP, PERSON, 0)).isPresent();
    }

    @Test
    void batchReturnsAllRequestedSnapshots() {
        recomputeService.recompute(APP, List.of(PERSON, OTHER));

        List<AuthzSnapshotView> views = appService.batch(APP, List.of(PERSON, OTHER));

        assertThat(views).extracting(AuthzSnapshotView::personId).containsExactlyInAnyOrder(PERSON, OTHER);
    }

    @Test
    void recomputeCmdWithEmptyPersonIdsRecomputesWholeApp() {
        recomputeService.recompute(APP, List.of(PERSON, OTHER));

        SnapshotRecomputeResult result = appService.recompute(new SnapshotRecomputeCmd(APP, List.of()));

        assertThat(result.recomputed()).isEqualTo(2);
        assertThat(snapshots.version(PERSON, APP)).contains(2L);
    }
}
