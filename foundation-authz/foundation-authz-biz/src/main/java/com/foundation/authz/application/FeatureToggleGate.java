package com.foundation.authz.application;

import com.foundation.authz.api.FeatureToggleApi;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.Subsystem;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.authz.domain.ToggleScopeType;
import com.foundation.authz.infrastructure.FunctionRepository;
import com.foundation.authz.infrastructure.MenuRepository;
import com.foundation.authz.infrastructure.ModuleRepository;
import com.foundation.authz.infrastructure.SubsystemRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 功能开关生效范围解析（F-05 BAS-27 §功能开关「三处一致」的 BASIS 侧）。
 *
 * <p>把「子系统 / 模块 / 功能」的停用范围（经 {@link FeatureToggleApi#disabled} 继承解析）翻译为
 * 应用内受影响的菜单与权限码，供授权快照（{@link SnapshotAssembler}）与授权查询
 * （{@link AuthorizationQueryService}）剔除，子系统不自判。
 */
public class FeatureToggleGate {

    private final FeatureToggleApi toggles;
    private final SubsystemRepository subsystems;
    private final ModuleRepository modules;
    private final FunctionRepository functions;
    private final MenuRepository menus;

    public FeatureToggleGate(FeatureToggleApi toggles, SubsystemRepository subsystems,
                             ModuleRepository modules, FunctionRepository functions, MenuRepository menus) {
        this.toggles = toggles;
        this.subsystems = subsystems;
        this.modules = modules;
        this.functions = functions;
        this.menus = menus;
    }

    /** 应用内被停用范围影响的菜单 id（功能菜单及其全部下级菜单）。 */
    public Set<String> disabledMenuIds(String appId) {
        if (appId == null) {
            return Set.of();
        }
        Subsystem subsystem = subsystems.findByAppId(appId).orElse(null);
        if (subsystem == null) {
            return Set.of();
        }
        Set<String> roots = new LinkedHashSet<>();
        for (SubsystemModule module : modules.listBySubsystem(subsystem.id())) {
            for (SubsystemFunction function : functions.listByModule(module.id())) {
                if (function.menuId() != null
                        && toggles.disabled(ToggleScopeType.FUNCTION.name(), function.id())) {
                    roots.add(function.menuId());
                }
            }
        }
        return roots.isEmpty() ? Set.of() : expand(roots, menus.listByApp(appId));
    }

    /** 应用内被停用范围影响的权限码（功能菜单及其下级菜单所关联的权限码）。 */
    public Set<String> disabledPermissionCodes(String appId) {
        Set<String> disabledMenus = disabledMenuIds(appId);
        if (disabledMenus.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Menu menu : menus.listByApp(appId)) {
            String code = menu.permissionCode();
            if (disabledMenus.contains(menu.id()) && code != null && !code.isBlank()) {
                codes.add(code);
            }
        }
        return codes;
    }

    private static Set<String> expand(Set<String> roots, List<Menu> appMenus) {
        Set<String> result = new LinkedHashSet<>(roots);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Menu menu : appMenus) {
                if (menu.parentId() != null && result.contains(menu.parentId()) && result.add(menu.id())) {
                    changed = true;
                }
            }
        }
        return result;
    }
}
