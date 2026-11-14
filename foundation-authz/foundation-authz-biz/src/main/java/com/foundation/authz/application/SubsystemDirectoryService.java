package com.foundation.authz.application;

import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppView;
import com.foundation.authz.api.DirectoryApi;
import com.foundation.authz.api.DirectoryNodeView;
import com.foundation.authz.api.DirectoryView;
import com.foundation.authz.api.FeatureToggleApi;
import com.foundation.authz.api.SubsystemApi;
import com.foundation.authz.api.SubsystemView;
import com.foundation.authz.config.SubsystemProperties;
import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.AuthModeRules;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.MenuType;
import com.foundation.authz.domain.SelfImportStatus;
import com.foundation.authz.domain.Subsystem;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.authz.domain.ToggleScopeType;
import com.foundation.authz.infrastructure.FunctionRepository;
import com.foundation.authz.infrastructure.MenuRepository;
import com.foundation.authz.infrastructure.ModuleRepository;
import com.foundation.authz.infrastructure.SelfManagedImportRepository;
import com.foundation.authz.infrastructure.SubsystemRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 子系统与目录（F-05 BAS-27 §目录/§四种授权模式）。
 *
 * <p>目录层级：子系统 → 模块（{@code fdn_module}）→ 功能（{@code fdn_function}）→ 操作
 * （功能所关联菜单下的按钮菜单，对应权限码）。授权模式切换规则见 {@link AuthModeRules}。
 */
public class SubsystemDirectoryService implements SubsystemApi, DirectoryApi {

    private static final String LEVEL_MODULE = "MODULE";
    private static final String LEVEL_FUNCTION = "FUNCTION";
    private static final String LEVEL_OPERATION = "OPERATION";

    private final SubsystemRepository subsystems;
    private final ModuleRepository modules;
    private final FunctionRepository functions;
    private final SelfManagedImportRepository imports;
    private final MenuRepository menus;
    private final FeatureToggleApi toggles;
    private final AppApi apps;
    private final AppIdResolver appIds;
    private final SubsystemProperties properties;

    public SubsystemDirectoryService(SubsystemRepository subsystems, ModuleRepository modules,
                                     FunctionRepository functions, SelfManagedImportRepository imports,
                                     MenuRepository menus, FeatureToggleApi toggles, AppApi apps,
                                     AppIdResolver appIds, SubsystemProperties properties) {
        this.subsystems = subsystems;
        this.modules = modules;
        this.functions = functions;
        this.imports = imports;
        this.menus = menus;
        this.toggles = toggles;
        this.apps = apps;
        this.appIds = appIds;
        this.properties = properties;
    }

    @Override
    public List<SubsystemView> list() {
        Map<String, AppView> byId = appIndex();
        return subsystems.list().stream()
                .map(subsystem -> toView(subsystem, byId.get(subsystem.appId())))
                .toList();
    }

    @Override
    public Optional<SubsystemView> find(String appCode) {
        String appId = resolveAppId(appCode);
        return subsystems.findByAppId(appId).map(subsystem -> toView(subsystem, appById(appId)));
    }

    @Override
    @Transactional
    public SubsystemView changeAuthMode(String appCode, String authMode, String operator) {
        Subsystem subsystem = loadOrProvision(appCode);
        AuthMode target = AuthMode.of(authMode);
        AuthModeRules.assertTransition(subsystem.authMode(), target, hasAppliedImport(subsystem.id()));
        if (subsystem.authMode() != target) {
            subsystems.updateAuthMode(subsystem.id(), target);
        }
        return toView(subsystem.withAuthMode(target), appById(subsystem.appId()));
    }

    @Override
    public DirectoryView directory(String appCode) {
        Subsystem subsystem = loadOrProvision(appCode);
        List<DirectoryNodeView> moduleNodes = modules.listBySubsystem(subsystem.id()).stream()
                .filter(SubsystemModule::active)
                .map(this::moduleNode)
                .toList();
        return new DirectoryView(appCode, subsystem.id(), subsystem.authMode().name(), moduleNodes);
    }

    private DirectoryNodeView moduleNode(SubsystemModule module) {
        boolean enabled = !toggles.disabled(ToggleScopeType.MODULE.name(), module.id());
        List<DirectoryNodeView> functionNodes = functions.listByModule(module.id()).stream()
                .filter(SubsystemFunction::active)
                .map(this::functionNode)
                .toList();
        return new DirectoryNodeView(LEVEL_MODULE, module.code(), module.name(), null, enabled, functionNodes);
    }

    private DirectoryNodeView functionNode(SubsystemFunction function) {
        boolean enabled = !toggles.disabled(ToggleScopeType.FUNCTION.name(), function.id());
        return new DirectoryNodeView(LEVEL_FUNCTION, function.code(), function.name(), function.menuId(), enabled,
                operationNodes(function, enabled));
    }

    /** 操作 = 功能所关联菜单下的按钮菜单（对应权限码）；功能无关联菜单时为空。 */
    private List<DirectoryNodeView> operationNodes(SubsystemFunction function, boolean functionEnabled) {
        if (function.menuId() == null) {
            return List.of();
        }
        Optional<Menu> menu = menus.findById(function.menuId());
        if (menu.isEmpty()) {
            return List.of();
        }
        String appId = menu.get().appId();
        List<DirectoryNodeView> operations = new ArrayList<>();
        for (Menu candidate : menus.listByApp(appId)) {
            if (menu.get().id().equals(candidate.parentId())
                    && MenuType.BUTTON.name().equals(candidate.type())) {
                String code = candidate.permissionCode() == null || candidate.permissionCode().isBlank()
                        ? candidate.code()
                        : candidate.permissionCode();
                operations.add(new DirectoryNodeView(LEVEL_OPERATION, code, candidate.name(), candidate.id(),
                        functionEnabled, List.of()));
            }
        }
        return operations;
    }

    /** 取子系统；不存在时按默认授权模式开户（{@code fdn_subsystem} 与 {@code fdn_app} 1:1）。 */
    private Subsystem loadOrProvision(String appCode) {
        String appId = resolveAppId(appCode);
        return subsystems.findByAppId(appId).orElseGet(() -> {
            Subsystem created = new Subsystem(Ids.newId(AuthzIdPrefix.SUBSYSTEM), appId,
                    AuthMode.of(properties.getDefaultAuthMode()), true, null);
            subsystems.insert(created);
            return created;
        });
    }

    private boolean hasAppliedImport(String subsystemId) {
        return imports.listBySubsystem(subsystemId).stream()
                .anyMatch(batch -> batch.status() == SelfImportStatus.APPLIED);
    }

    private String resolveAppId(String appCode) {
        return appIds.resolve(appCode).orElseThrow(
                () -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + appCode));
    }

    private AppView appById(String appId) {
        return appIndex().get(appId);
    }

    private Map<String, AppView> appIndex() {
        Map<String, AppView> byId = new LinkedHashMap<>();
        for (AppView app : apps.list()) {
            byId.put(app.id(), app);
        }
        return byId;
    }

    private static SubsystemView toView(Subsystem subsystem, AppView app) {
        return new SubsystemView(subsystem.id(), subsystem.appId(),
                app == null ? null : app.code(), app == null ? null : app.name(),
                subsystem.authMode().name(), subsystem.portalVisible(), subsystem.adminUserId());
    }
}
