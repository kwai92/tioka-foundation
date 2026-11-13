package com.foundation.authz.domain;

import java.util.List;

/**
 * 清单与既有目录的差异（F-05 BAS-05 §清单导入规则三分支）。
 *
 * <p>新增项直接生效、默认不授予任何角色；消失项标 {@code OFFLINE}；变更项只更新清单来源字段。
 *
 * @param addedMenus         新增菜单
 * @param changedMenus       内容有变的菜单（含曾被下线、本次重新声明者）
 * @param offlineMenus       消失的在线菜单
 * @param addedPermissions   新增权限码
 * @param changedPermissions 内容有变的权限码
 * @param offlinePermissions 消失的在线权限码
 */
public record ManifestDiff(List<ManifestMenu> addedMenus,
                           List<ChangedMenu> changedMenus,
                           List<Menu> offlineMenus,
                           List<ManifestPermission> addedPermissions,
                           List<ChangedPermission> changedPermissions,
                           List<PermissionCode> offlinePermissions) {

    public ManifestDiff {
        addedMenus = addedMenus == null ? List.of() : List.copyOf(addedMenus);
        changedMenus = changedMenus == null ? List.of() : List.copyOf(changedMenus);
        offlineMenus = offlineMenus == null ? List.of() : List.copyOf(offlineMenus);
        addedPermissions = addedPermissions == null ? List.of() : List.copyOf(addedPermissions);
        changedPermissions = changedPermissions == null ? List.of() : List.copyOf(changedPermissions);
        offlinePermissions = offlinePermissions == null ? List.of() : List.copyOf(offlinePermissions);
    }

    /** 本次被标下线的项数（菜单 + 权限码）。 */
    public int offlineCount() {
        return offlineMenus.size() + offlinePermissions.size();
    }

    /** 清单变更的菜单条目（声明侧 + 库内侧）。 */
    public record ChangedMenu(ManifestMenu declared, Menu existing) {
    }

    /** 清单变更的权限码条目（声明侧 + 库内侧）。 */
    public record ChangedPermission(ManifestPermission declared, PermissionCode existing) {
    }
}
