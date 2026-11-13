package com.foundation.authz.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 授权快照（F-06 BAS-07 §粒度与内容）：一个人员在一个应用里一份快照。
 *
 * <p>由 {@code SnapshotAssembler} 汇总有效角色、权限码、可见菜单、数据范围与人员属性后产出，
 * 经 {@code SnapshotRepository} 落 {@code fdn_authz_snapshot}（{@code body} 为 JSONB）。
 *
 * @param personId    人员 ID
 * @param appCode     应用编码
 * @param version     版本号（按 {@code (personId, appCode)} 单调递增）
 * @param roles       有效角色编码
 * @param permissions 权限码集合
 * @param menus       按权限过滤后的可见菜单树
 * @param deptScope   部门范围
 * @param dimensions  各维度最终取值，键为维度编码
 * @param person      人员属性
 * @param computedAt  计算时刻
 */
public record AuthzSnapshot(String personId,
                            String appCode,
                            long version,
                            Set<String> roles,
                            Set<String> permissions,
                            List<SnapshotMenu> menus,
                            DataScopeResolution.Scope deptScope,
                            Map<String, DataScopeResolution.Scope> dimensions,
                            SnapshotPerson person,
                            Instant computedAt) {

    public AuthzSnapshot {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(appCode, "appCode");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        menus = menus == null ? List.of() : List.copyOf(menus);
        deptScope = deptScope == null ? DataScopeResolution.Scope.of(Set.of()) : deptScope;
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
        person = person == null ? new SnapshotPerson(null, List.of(), null) : person;
        computedAt = computedAt == null ? Instant.now() : computedAt;
    }
}
