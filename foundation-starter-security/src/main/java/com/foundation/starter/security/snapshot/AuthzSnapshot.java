package com.foundation.starter.security.snapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 授权快照（F-07 §4；内容见 F-06 §粒度与内容）。
 *
 * <p>一个人员在一个应用里一份快照；{@code version} 按 {@code (personId, appCode)} 单调递增。
 * 字段名与 Foundation 侧 {@code AuthzSnapshotView} 对齐，便于直接反序列化。
 *
 * @param personId    人员 ID
 * @param appCode     应用编码
 * @param version     快照版本号
 * @param roles       有效角色编码
 * @param permissions 权限码集合
 * @param menus       按权限过滤后的可见菜单树
 * @param deptScope   部门范围
 * @param dimensions  各维度取值，键为维度编码
 * @param person      人员属性
 */
public record AuthzSnapshot(String personId,
                            String appCode,
                            long version,
                            Set<String> roles,
                            Set<String> permissions,
                            List<MenuNode> menus,
                            DeptScope deptScope,
                            Map<String, DimensionScope> dimensions,
                            PersonAttrs person) {

    public AuthzSnapshot {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        menus = menus == null ? List.of() : List.copyOf(menus);
        deptScope = deptScope == null ? DeptScope.ofAll() : deptScope;
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }

    /** 是否具备某权限码。 */
    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    /** 是否**同时**具备全部权限码（F-07 §3：多个值为「全部需要」）。 */
    public boolean hasAll(String... codes) {
        for (String code : codes) {
            if (!permissions.contains(code)) {
                return false;
            }
        }
        return true;
    }
}
