package com.foundation.authz.application;

import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.ManifestDiff;
import com.foundation.authz.domain.ManifestDocument;
import com.foundation.authz.domain.ManifestMenu;
import com.foundation.authz.domain.ManifestPermission;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.PermissionCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 清单与既有目录的差异计算（F-05 BAS-05 §清单导入规则三分支）。
 *
 * <p>纯规则：只比对声明与库内数据，不读写任何仓储。判定口径：
 * <ul>
 *   <li><b>新增</b>：库内无同编码项；</li>
 *   <li><b>消失</b>：库内在线的项本次未声明；曾下线项不再重复标下线；</li>
 *   <li><b>变更</b>：库内在线的项，清单来源字段（类型、上级、路由、组件、权限码 / 说明、风险级别）
 *       有差异；曾下线项被重新声明也视为变更（需复位在线）。管理台可改的名称、图标、排序、
 *       显示不参与比对，重导清单不覆盖它们。</li>
 * </ul>
 */
public class ManifestDiffCalculator {

    /** 比对声明与库内数据，产出差异。 */
    public ManifestDiff diff(ManifestDocument declared, List<Menu> existingMenus,
                             List<PermissionCode> existingPermissions) {
        return new ManifestDiff(
                addedMenus(declared, existingMenus),
                changedMenus(declared, existingMenus),
                offlineMenus(declared, existingMenus),
                addedPermissions(declared, existingPermissions),
                changedPermissions(declared, existingPermissions),
                offlinePermissions(declared, existingPermissions));
    }

    private static List<ManifestMenu> addedMenus(ManifestDocument declared, List<Menu> existingMenus) {
        Map<String, Menu> existing = indexMenus(existingMenus);
        List<ManifestMenu> added = new ArrayList<>();
        for (ManifestMenu menu : declared.menus()) {
            if (!existing.containsKey(menu.code())) {
                added.add(menu);
            }
        }
        return added;
    }

    private static List<ManifestDiff.ChangedMenu> changedMenus(ManifestDocument declared,
                                                               List<Menu> existingMenus) {
        Map<String, Menu> existing = indexMenus(existingMenus);
        Map<String, String> idByCode = new LinkedHashMap<>();
        existing.forEach((code, menu) -> idByCode.put(code, menu.id()));

        List<ManifestDiff.ChangedMenu> changed = new ArrayList<>();
        for (ManifestMenu menu : declared.menus()) {
            Menu found = existing.get(menu.code());
            if (found != null && menuChanged(menu, found, idByCode)) {
                changed.add(new ManifestDiff.ChangedMenu(menu, found));
            }
        }
        return changed;
    }

    private static List<Menu> offlineMenus(ManifestDocument declared, List<Menu> existingMenus) {
        Set<String> declaredCodes = declared.menusByCode().keySet();
        List<Menu> offline = new ArrayList<>();
        for (Menu menu : existingMenus) {
            if (LifecycleStatus.ACTIVE.equals(menu.status()) && !declaredCodes.contains(menu.code())) {
                offline.add(menu);
            }
        }
        return offline;
    }

    private static List<ManifestPermission> addedPermissions(ManifestDocument declared,
                                                             List<PermissionCode> existingPermissions) {
        Map<String, PermissionCode> existing = indexPermissions(existingPermissions);
        List<ManifestPermission> added = new ArrayList<>();
        for (ManifestPermission permission : declared.permissions()) {
            if (!existing.containsKey(permission.code())) {
                added.add(permission);
            }
        }
        return added;
    }

    private static List<ManifestDiff.ChangedPermission> changedPermissions(
            ManifestDocument declared, List<PermissionCode> existingPermissions) {
        Map<String, PermissionCode> existing = indexPermissions(existingPermissions);
        List<ManifestDiff.ChangedPermission> changed = new ArrayList<>();
        for (ManifestPermission permission : declared.permissions()) {
            PermissionCode found = existing.get(permission.code());
            if (found != null && permissionChanged(permission, found)) {
                changed.add(new ManifestDiff.ChangedPermission(permission, found));
            }
        }
        return changed;
    }

    private static List<PermissionCode> offlinePermissions(ManifestDocument declared,
                                                           List<PermissionCode> existingPermissions) {
        Set<String> declaredCodes = declared.permissionsByCode().keySet();
        List<PermissionCode> offline = new ArrayList<>();
        for (PermissionCode permission : existingPermissions) {
            if (permission.active() && !declaredCodes.contains(permission.code())) {
                offline.add(permission);
            }
        }
        return offline;
    }

    /** 清单来源字段是否有变；显示属性不参与。 */
    private static boolean menuChanged(ManifestMenu declared, Menu existing, Map<String, String> idByCode) {
        if (!LifecycleStatus.ACTIVE.equals(existing.status())) {
            return true;
        }
        return !Objects.equals(declared.type().name(), existing.type())
                || !Objects.equals(parentIdOf(declared, idByCode), existing.parentId())
                || !Objects.equals(declared.routePath(), existing.routePath())
                || !Objects.equals(declared.component(), existing.component())
                || !Objects.equals(declared.permissionCode(), existing.permissionCode());
    }

    private static boolean permissionChanged(ManifestPermission declared, PermissionCode existing) {
        if (!existing.active()) {
            return true;
        }
        return !Objects.equals(declared.name(), existing.name())
                || declared.riskLevel() != existing.riskLevel();
    }

    private static String parentIdOf(ManifestMenu declared, Map<String, String> idByCode) {
        return declared.parentCode() == null ? null : idByCode.get(declared.parentCode());
    }

    private static Map<String, Menu> indexMenus(List<Menu> menus) {
        Map<String, Menu> byCode = new LinkedHashMap<>();
        for (Menu menu : menus) {
            byCode.put(menu.code(), menu);
        }
        return byCode;
    }

    private static Map<String, PermissionCode> indexPermissions(List<PermissionCode> permissions) {
        Map<String, PermissionCode> byCode = new LinkedHashMap<>();
        for (PermissionCode permission : permissions) {
            byCode.put(permission.code(), permission);
        }
        return byCode;
    }
}
