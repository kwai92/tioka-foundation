package com.foundation.authz.domain;

/**
 * 清单声明的菜单（F-05 BAS-05 §清单导入规则）。
 *
 * @param code           菜单编码（应用内唯一，声明期以编码关联上级）
 * @param name           名称
 * @param type           菜单类型
 * @param parentCode     上级菜单编码，可空
 * @param routePath      路由路径，可空
 * @param component      前端组件标识，可空
 * @param icon           图标，可空
 * @param sort           排序
 * @param visible        是否显示
 * @param permissionCode 关联权限码，可空
 */
public record ManifestMenu(String code,
                           String name,
                           MenuType type,
                           String parentCode,
                           String routePath,
                           String component,
                           String icon,
                           int sort,
                           boolean visible,
                           String permissionCode) {
}
