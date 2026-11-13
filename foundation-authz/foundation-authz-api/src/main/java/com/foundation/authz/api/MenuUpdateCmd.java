package com.foundation.authz.api;

/**
 * 菜单显示属性调整命令（F-05 BAS-05 §接口 {@code PUT /api/menus/{id}}）。
 *
 * <p>只允许改名称、图标、排序、显示；清单来源字段（路由/组件/类型/上级）不在此列。
 *
 * @param id       菜单 ID
 * @param name     新名称，{@code null} 表示不变
 * @param icon     新图标，{@code null} 表示不变
 * @param sort     新排序，{@code null} 表示不变
 * @param visible  新显示标志，{@code null} 表示不变
 * @param operator 操作人
 */
public record MenuUpdateCmd(String id,
                            String name,
                            String icon,
                            Integer sort,
                            Boolean visible,
                            String operator) {
}
