package com.foundation.authz.application;

import com.foundation.authz.config.SubsystemProperties;
import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.FeatureToggle;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.Subsystem;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.authz.domain.ToggleScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 功能开关生效范围解析（F-05 BAS-27 §功能开关「快照剔除」，含作用域继承）。 */
class FeatureToggleGateTest {

    private static final String APP = "app_a";

    private final TestDoubles.InMemorySubsystemRepository subsystems =
            new TestDoubles.InMemorySubsystemRepository();
    private final TestDoubles.InMemoryModuleRepository modules = new TestDoubles.InMemoryModuleRepository();
    private final TestDoubles.InMemoryFunctionRepository functions =
            new TestDoubles.InMemoryFunctionRepository();
    private final TestDoubles.InMemoryMenuRepository menus = new TestDoubles.InMemoryMenuRepository();
    private final TestDoubles.InMemoryFeatureToggleRepository toggles =
            new TestDoubles.InMemoryFeatureToggleRepository();

    private FeatureToggleGate gate;

    @BeforeEach
    void setUp() {
        subsystems.insert(new Subsystem("sub_1", "app_a", AuthMode.CONCENTRATED, true, null));
        modules.insert(new SubsystemModule("mod_1", "sub_1", "workflow", "工作流", 0, "ACTIVE"));
        functions.insert(new SubsystemFunction("fun_1", "mod_1", "wf.flow", "流程管理", "men_flow", 0,
                "ACTIVE"));
        menus.insert(menu("men_flow", "flow", "MENU", null, null));
        menus.insert(menu("men_submit", "submit", "BUTTON", "men_flow", "c:submit"));

        FeatureToggleService service = new FeatureToggleService(toggles, modules, functions,
                new SubsystemProperties());
        gate = new FeatureToggleGate(service, subsystems, modules, functions, menus);
    }

    @Test
    void noToggleMeansNothingAffected() {
        assertThat(gate.disabledMenuIds(APP)).isEmpty();
        assertThat(gate.disabledPermissionCodes(APP)).isEmpty();
    }

    @Test
    void disabledFunctionRemovesMenuSubtreeAndPermissionCodes() {
        disable(ToggleScopeType.FUNCTION, "fun_1");

        assertThat(gate.disabledMenuIds(APP)).containsExactlyInAnyOrder("men_flow", "men_submit");
        assertThat(gate.disabledPermissionCodes(APP)).containsExactly("c:submit");
    }

    @Test
    void disabledModuleCascadesToItsFunctions() {
        disable(ToggleScopeType.MODULE, "mod_1");

        assertThat(gate.disabledMenuIds(APP)).containsExactlyInAnyOrder("men_flow", "men_submit");
    }

    @Test
    void disabledSubsystemCascadesToModules() {
        disable(ToggleScopeType.SUBSYSTEM, "sub_1");

        assertThat(gate.disabledPermissionCodes(APP)).containsExactly("c:submit");
    }

    @Test
    void functionExplicitEnableOverridesDisabledModule() {
        disable(ToggleScopeType.MODULE, "mod_1");
        toggles.insert(new FeatureToggle("tog_on", ToggleScopeType.FUNCTION, "fun_1", true, "恢复"));

        assertThat(gate.disabledMenuIds(APP)).isEmpty();
    }

    @Test
    void unknownAppOrSubsystemYieldsNothing() {
        assertThat(gate.disabledMenuIds(null)).isEmpty();
        assertThat(gate.disabledMenuIds("app_other")).isEmpty();
    }

    private void disable(ToggleScopeType scopeType, String scopeId) {
        toggles.insert(new FeatureToggle("tog_" + scopeId, scopeType, scopeId, false, "停用"));
    }

    private static Menu menu(String id, String code, String type, String parentId, String permissionCode) {
        return new Menu(id, "app_a", code, code, type, parentId, null, null, null, 0, true, permissionCode,
                LifecycleStatus.ACTIVE);
    }
}
