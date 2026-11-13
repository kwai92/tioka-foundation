package com.foundation.authz.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 授权快照视图（F-06 BAS-07 §粒度与内容）：一个人员在一个应用里一份快照。
 *
 * @param personId    人员 ID
 * @param appCode     应用编码
 * @param version     快照版本号（按 {@code (personId, appCode)} 单调递增）
 * @param roles       有效角色编码
 * @param permissions 权限码集合
 * @param menus       按权限过滤后的可见菜单树
 * @param deptScope   部门范围（{@code all} 或展开后的组织单元 ID 集合）
 * @param dimensions  各维度最终取值集合，键为维度编码
 * @param person      人员属性（主组织单元、岗位、职级）
 * @param computedAt  计算时刻
 */
public record AuthzSnapshotView(String personId,
                                String appCode,
                                long version,
                                List<String> roles,
                                List<String> permissions,
                                List<MenuNode> menus,
                                ScopeView deptScope,
                                Map<String, ScopeView> dimensions,
                                PersonAttributes person,
                                Instant computedAt) {

    public AuthzSnapshotView {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        menus = menus == null ? List.of() : List.copyOf(menus);
        deptScope = deptScope == null ? ScopeView.of(List.of()) : deptScope;
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }

    /**
     * 可见菜单节点（目录/菜单/按钮）。
     *
     * @param code     菜单编码
     * @param type     {@code DIR} / {@code MENU} / {@code BUTTON}
     * @param children 下级节点
     */
    public record MenuNode(String code, String type, List<MenuNode> children) {

        public MenuNode {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    /**
     * 人员属性（同时作为 GOV-07 Cedar 与 ADR-040 CEL 的属性来源）。
     *
     * @param primaryOrgUnit 主组织单元，可空
     * @param positions      岗位集合
     * @param grade          职级，可空
     */
    public record PersonAttributes(String primaryOrgUnit, List<String> positions, String grade) {

        public PersonAttributes {
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }
}
