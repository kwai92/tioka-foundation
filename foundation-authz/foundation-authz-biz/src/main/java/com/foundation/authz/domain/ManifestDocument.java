package com.foundation.authz.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限清单文档（F-05 BAS-05 §清单导入规则）：应用声明其菜单与权限码。
 *
 * <p>版本必须大于 {@code fdn_app.manifest_version}，否则导入被拒（{@code CONFLICT}）。
 *
 * @param version     清单版本
 * @param menus       声明的菜单
 * @param permissions 声明的权限码
 */
public record ManifestDocument(long version, List<ManifestMenu> menus, List<ManifestPermission> permissions) {

    public ManifestDocument {
        menus = menus == null ? List.of() : List.copyOf(menus);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /** 菜单按编码索引。 */
    public Map<String, ManifestMenu> menusByCode() {
        Map<String, ManifestMenu> byCode = new LinkedHashMap<>();
        for (ManifestMenu menu : menus) {
            byCode.put(menu.code(), menu);
        }
        return byCode;
    }

    /** 权限码按编码索引。 */
    public Map<String, ManifestPermission> permissionsByCode() {
        Map<String, ManifestPermission> byCode = new LinkedHashMap<>();
        for (ManifestPermission permission : permissions) {
            byCode.put(permission.code(), permission);
        }
        return byCode;
    }
}
