package com.foundation.authz.application;

import com.foundation.authz.domain.AffectedPersonResolver;
import com.foundation.authz.domain.AuthzSnapshot;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.infrastructure.AuthzChangedEventPublisher;
import com.foundation.authz.infrastructure.SnapshotRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权快照重算（F-06 BAS-07 §快照计算）。
 *
 * <p>版本按 {@code (personId, appCode)} 单调递增；重算即写快照并（同事务）发
 * {@code fdn.authz.changed} 事件。增量重算只覆盖 {@link AffectedPersonResolver} 判定的受影响人员；
 * {@link #recomputeApp(String)} 作为每日全量兜底与手动全量重算的入口。
 */
public class SnapshotRecomputeService {

    private final SnapshotRepository snapshots;
    private final SnapshotAssembler assembler;
    private final AuthzChangedEventPublisher publisher;
    private final AffectedPersonResolver affected;

    public SnapshotRecomputeService(SnapshotRepository snapshots, SnapshotAssembler assembler,
                                    AuthzChangedEventPublisher publisher, AffectedPersonResolver affected) {
        this.snapshots = snapshots;
        this.assembler = assembler;
        this.publisher = publisher;
        this.affected = affected;
    }

    /** 重算某人员在某应用的快照：版本 +1、落库并发事件。 */
    @Transactional
    public AuthzSnapshot recompute(String personId, String appCode) {
        long version = snapshots.version(personId, appCode).orElse(0L) + 1;
        AuthzSnapshot snapshot = assembler.assemble(personId, appCode, version);
        snapshots.upsert(snapshot);
        publisher.publish(snapshot);
        return snapshot;
    }

    /** 重算一批人员在某应用的快照，返回实际重算数。 */
    @Transactional
    public int recompute(String appCode, Collection<String> personIds) {
        int count = 0;
        for (String personId : distinct(personIds)) {
            recompute(personId, appCode);
            count++;
        }
        return count;
    }

    /** 按应用全量重算（对已有快照的人员兜底），返回实际重算数。 */
    @Transactional
    public int recomputeApp(String appCode) {
        return recompute(appCode, snapshots.listPersonsByApp(appCode));
    }

    /** 授权变化：只重算授予对象覆盖的人员。 */
    @Transactional
    public int recomputeForGrant(String appCode, RoleGrant grant) {
        return recompute(appCode, affected.forGrant(grant));
    }

    /** 角色定义变化：只重算该角色授予对象覆盖的人员。 */
    @Transactional
    public int recomputeForRoleDefinition(String appCode, String roleCode, List<RoleGrant> grants) {
        return recompute(appCode, affected.forRoleDefinition(roleCode, grants));
    }

    /** 任职变化：只重算该人员。 */
    @Transactional
    public int recomputeForMembership(String appCode, String personId) {
        return recompute(appCode, affected.forMembershipChange(personId));
    }

    /** 组织树变化：只重算该单元（及下级）下的任职人员。 */
    @Transactional
    public int recomputeForOrgTree(String appCode, String orgUnitId) {
        return recompute(appCode, affected.forOrgTreeChange(orgUnitId));
    }

    /** 清单变化：重算该应用的全部已登记人员。 */
    @Transactional
    public int recomputeForManifest(String appCode, Collection<String> registeredPersons) {
        return recompute(appCode, affected.forManifestChange(registeredPersons));
    }

    private static Set<String> distinct(Collection<String> personIds) {
        Set<String> result = new LinkedHashSet<>();
        if (personIds != null) {
            for (String personId : personIds) {
                if (personId != null && !personId.isBlank()) {
                    result.add(personId);
                }
            }
        }
        return result;
    }
}
