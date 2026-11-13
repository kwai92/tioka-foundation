package com.foundation.authz.application;

import com.foundation.authz.api.AuthzSnapshotView;
import com.foundation.authz.api.ScopeView;
import com.foundation.authz.api.SnapshotApi;
import com.foundation.authz.api.SnapshotRecomputeCmd;
import com.foundation.authz.api.SnapshotRecomputeResult;
import com.foundation.authz.domain.AuthzSnapshot;
import com.foundation.authz.domain.DataScopeResolution;
import com.foundation.authz.domain.SnapshotMenu;
import com.foundation.authz.domain.SnapshotPerson;
import com.foundation.authz.infrastructure.SnapshotRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 授权快照读取与手动重算（F-06 BAS-07 §接口 {@code /api/fdn}）。
 *
 * <p>读路径命中即返回；不存在或快照内容缺失时按需重算回填。应用以服务账号调用，
 * 靠 starter（BAS-08）本地缓存运行，Foundation 不在请求热路径。
 */
public class SnapshotAppService implements SnapshotApi {

    private final SnapshotRepository snapshots;
    private final SnapshotRecomputeService recomputeService;

    public SnapshotAppService(SnapshotRepository snapshots, SnapshotRecomputeService recomputeService) {
        this.snapshots = snapshots;
        this.recomputeService = recomputeService;
    }

    @Override
    public Optional<AuthzSnapshotView> find(String appCode, String personId) {
        Optional<AuthzSnapshot> existing = snapshots.find(personId, appCode);
        if (existing.isPresent()) {
            return existing.map(SnapshotAppService::toView);
        }
        return Optional.of(toView(recomputeService.recompute(personId, appCode)));
    }

    @Override
    public Optional<AuthzSnapshotView> findIfNewer(String appCode, String personId, long knownVersion) {
        Optional<Long> version = snapshots.version(personId, appCode);
        if (version.isPresent() && version.get() == knownVersion) {
            return Optional.empty();
        }
        return find(appCode, personId);
    }

    @Override
    public List<AuthzSnapshotView> batch(String appCode, List<String> personIds) {
        List<AuthzSnapshotView> result = new ArrayList<>();
        if (personIds == null) {
            return result;
        }
        for (String personId : personIds) {
            find(appCode, personId).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public Optional<Long> version(String appCode, String personId) {
        return snapshots.version(personId, appCode);
    }

    @Override
    public SnapshotRecomputeResult recompute(SnapshotRecomputeCmd cmd) {
        int count = cmd.personIds().isEmpty()
                ? recomputeService.recomputeApp(cmd.appCode())
                : recomputeService.recompute(cmd.appCode(), cmd.personIds());
        return new SnapshotRecomputeResult(count);
    }

    private static AuthzSnapshotView toView(AuthzSnapshot snapshot) {
        Map<String, ScopeView> dimensions = new LinkedHashMap<>();
        snapshot.dimensions().forEach((code, scope) -> dimensions.put(code, toScopeView(scope)));
        return new AuthzSnapshotView(snapshot.personId(), snapshot.appCode(), snapshot.version(),
                List.copyOf(new TreeSet<>(snapshot.roles())),
                List.copyOf(new TreeSet<>(snapshot.permissions())),
                toMenuNodes(snapshot.menus()),
                toScopeView(snapshot.deptScope()),
                dimensions,
                toPerson(snapshot.person()),
                snapshot.computedAt());
    }

    private static List<AuthzSnapshotView.MenuNode> toMenuNodes(List<SnapshotMenu> menus) {
        List<AuthzSnapshotView.MenuNode> nodes = new ArrayList<>();
        for (SnapshotMenu menu : menus) {
            nodes.add(new AuthzSnapshotView.MenuNode(menu.code(), menu.type(), toMenuNodes(menu.children())));
        }
        return nodes;
    }

    private static AuthzSnapshotView.PersonAttributes toPerson(SnapshotPerson person) {
        return new AuthzSnapshotView.PersonAttributes(person.primaryOrgUnit(), person.positions(), person.grade());
    }

    private static ScopeView toScopeView(DataScopeResolution.Scope scope) {
        return scope.all() ? ScopeView.ofAll() : ScopeView.of(scope.values());
    }
}
