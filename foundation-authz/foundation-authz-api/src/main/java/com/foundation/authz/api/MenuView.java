package com.foundation.authz.api;

/**
 * 菜单视图（F-05 BAS-05 §领域模型）。
 *
 * @param id             菜单 ID（{@code men_{ulid}}）
 * @param appId          所属应用
 * @param code           菜单编码（应用内唯一）
 * @param name           名称（管理台可改）
 * @param type           {@code DIR} / {@code MENU} / {@code BUTTON}
 * @param parentId       上级菜单，可空
 * @param routePath      路由路径，可空
 * @param component      前端组件标识，可空
 * @param icon           图标（管理台可改），可空
 * @param sort           排序（管理台可改）
 * @param visible        是否显示（管理台可改）
 * @param permissionCode 关联权限码，可空
 * @param status         {@code ACTIVE} / {@code OFFLINE}
 */
public record MenuView(String id,
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
}
