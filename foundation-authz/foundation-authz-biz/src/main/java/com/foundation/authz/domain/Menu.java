package com.foundation.authz.domain;

import java.util.List;
import java.util.Objects;

/**
 * 菜单（F-05 BAS-05 §领域模型）。
 *
 * <p>来自权限清单；管理台只能改名称、图标、排序、显示，清单来源字段
 * （类型、上级、路由、组件、权限码）以清单为准。
 *
 * @param id             菜单 ID（{@code men_{ulid}}）
 * @param appId          所属应用
 * @param code           菜单编码（应用内唯一）
 * @param name           名称（管理台可改）
 * @param type           见 {@link MenuType}
 * @param parentId       上级菜单 ID，可空
 * @param routePath      路由路径，可空
 * @param component      前端组件标识，可空
 * @param icon           图标（管理台可改），可空
 * @param sort           排序（管理台可改）
 * @param visible        是否显示（管理台可改）
 * @param permissionCode 关联权限码，可空
 * @param status         见 {@link LifecycleStatus}
 */
public record Menu(String id,
                   String appId,
                   String code,
                   String name,
                   String type,
                   String parentId,
                   String routePath,
                   String component,
                   String icon,
                   int sort,
                   boolean visible,
                   String permissionCode,
                   String status) {

    public Menu {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        status = status == null ? LifecycleStatus.ACTIVE : status;
    }

    /** 管理台调整显示属性：只改名称/图标/排序/显示，清单来源字段保持不变。 */
    public Menu withDisplay(String newName, String newIcon, Integer newSort, Boolean newVisible) {
        return new Menu(id, appId, code,
                newName == null ? name : newName,
                type, parentId, routePath, component,
                newIcon == null ? icon : newIcon,
                newSort == null ? sort : newSort,
                newVisible == null ? visible : newVisible,
                permissionCode, status);
    }

    /** 清单内声明的权限码（用于权限码目录校验）。 */
    public List<String> declaredPermissionCodes() {
        return permissionCode == null || permissionCode.isBlank() ? List.of() : List.of(permissionCode);
    }
}
