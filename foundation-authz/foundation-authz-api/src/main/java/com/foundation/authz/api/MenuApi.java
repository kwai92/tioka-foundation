package com.foundation.authz.api;

import java.util.List;

/**
 * 菜单契约（F-05 BAS-05 §接口；菜单来自清单，管理台只能改名称/图标/排序/显示）。
 *
 * <p>读 {@code fdn:menu:read}，写 {@code fdn:menu:admin}。
 */
public interface MenuApi {

    /** 应用菜单树（按上级与排序展开，仅 ACTIVE）。 */
    List<MenuView> menus(String appCode);

    /** 管理台调整菜单显示属性；路由/组件/类型/上级等清单来源字段不可改。 */
    MenuView updateMenu(MenuUpdateCmd cmd);
}
