package com.foundation.authz.application;

import com.foundation.authz.api.MenuApi;
import com.foundation.authz.api.MenuUpdateCmd;
import com.foundation.authz.api.MenuView;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.infrastructure.MenuRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 菜单查询与显示属性维护（F-05 BAS-05 §接口）。
 *
 * <p>菜单来自清单；管理台只能改名称、图标、排序、显示，清单来源字段（类型、上级、路由、组件、权限码）
 * 由清单覆盖（{@link MenuRepository#updateFromManifest}）。
 */
public class MenuService implements MenuApi {

    private final MenuRepository menus;
    private final AppIdResolver appIds;

    public MenuService(MenuRepository menus, AppIdResolver appIds) {
        this.menus = menus;
        this.appIds = appIds;
    }

    @Override
    public List<MenuView> menus(String appCode) {
        String appId = appIds.resolve(appCode)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST, "应用编码不能为空"));
        return menus.listByApp(appId).stream()
                .filter(menu -> LifecycleStatus.ACTIVE.equals(menu.status()))
                .sorted(Comparator.comparing(MenuService::parentKey)
                        .thenComparingInt(Menu::sort)
                        .thenComparing(Menu::code))
                .map(MenuService::toView)
                .toList();
    }

    @Override
    @Transactional
    public MenuView updateMenu(MenuUpdateCmd cmd) {
        Menu existing = menus.findById(cmd.id())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "菜单不存在：" + cmd.id()));
        Menu updated = existing.withDisplay(cmd.name(), cmd.icon(), cmd.sort(), cmd.visible());
        menus.updateDisplay(updated);
        return toView(updated);
    }

    /** 顶层菜单排在前面，同级按排序与编码。 */
    private static String parentKey(Menu menu) {
        return menu.parentId() == null ? "" : menu.parentId();
    }

    private static MenuView toView(Menu menu) {
        return new MenuView(menu.id(), menu.appId(), menu.code(), menu.name(), menu.type(), menu.parentId(),
                menu.routePath(), menu.component(), menu.icon(), menu.sort(), menu.visible(),
                menu.permissionCode(), menu.status());
    }
}
