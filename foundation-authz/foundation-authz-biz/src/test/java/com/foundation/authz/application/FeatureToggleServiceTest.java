package com.foundation.authz.application;

import com.foundation.authz.api.FeatureToggleView;
import com.foundation.authz.config.SubsystemProperties;
import com.foundation.authz.domain.FeatureToggle;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.authz.domain.ToggleScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 功能开关应用服务（F-05 BAS-27 §功能开关）。 */
class FeatureToggleServiceTest {

    private final TestDoubles.InMemoryFeatureToggleRepository toggles =
            new TestDoubles.InMemoryFeatureToggleRepository();
    private final TestDoubles.InMemoryModuleRepository modules = new TestDoubles.InMemoryModuleRepository();
    private final TestDoubles.InMemoryFunctionRepository functions =
            new TestDoubles.InMemoryFunctionRepository();
    private final SubsystemProperties properties = new SubsystemProperties();

    private FeatureToggleService service;

    @BeforeEach
    void setUp() {
        modules.insert(new SubsystemModule("mod_1", "sub_1", "workflow", "工作流", 0, "ACTIVE"));
        functions.insert(new SubsystemFunction("fun_1", "mod_1", "wf.flow", "工作流", "men_flow", 0, "ACTIVE"));
        service = new FeatureToggleService(toggles, modules, functions, properties);
    }

    @Test
    void subsystemDisabledPropagatesToModuleAndFunction() {
        toggles.insert(new FeatureToggle("tog_1", ToggleScopeType.SUBSYSTEM, "sub_1", false, "客户未购买"));

        assertThat(service.disabled("MODULE", "mod_1")).isTrue();
        assertThat(service.disabled("FUNCTION", "fun_1")).isTrue();
    }

    @Test
    void functionExplicitEnableOverridesDisabledModule() {
        toggles.insert(new FeatureToggle("tog_1", ToggleScopeType.MODULE, "mod_1", false, null));
        toggles.insert(new FeatureToggle("tog_2", ToggleScopeType.FUNCTION, "fun_1", true, null));

        assertThat(service.disabled("MODULE", "mod_1")).isTrue();
        assertThat(service.disabled("FUNCTION", "fun_1")).isFalse();
    }

    @Test
    void noConfigMeansNotDisabled() {
        assertThat(service.disabled("FUNCTION", "fun_1")).isFalse();
    }

    @Test
    void toggleInheritDisabledKeepsScopeLocal() {
        properties.setToggleInherit(false);
        toggles.insert(new FeatureToggle("tog_1", ToggleScopeType.SUBSYSTEM, "sub_1", false, null));

        assertThat(service.disabled("MODULE", "mod_1")).isFalse();
        assertThat(service.disabled("SUBSYSTEM", "sub_1")).isTrue();
    }

    @Test
    void setCreatesThenUpdatesSameRecord() {
        FeatureToggleView created = service.set("MODULE", "mod_1", false, "停用工作流", "admin");
        assertThat(created.enabled()).isFalse();
        assertThat(created.scopeType()).isEqualTo("MODULE");

        FeatureToggleView updated = service.set("MODULE", "mod_1", true, "恢复", "admin");

        assertThat(updated.enabled()).isTrue();
        assertThat(toggles.list()).hasSize(1);
        assertThat(toggles.find(ToggleScopeType.MODULE, "mod_1").orElseThrow().reason()).isEqualTo("恢复");
        assertThat(service.disabled("MODULE", "mod_1")).isFalse();
    }

    @Test
    void listReturnsConfiguredToggles() {
        service.set("SUBSYSTEM", "sub_1", false, "停用", "admin");

        assertThat(service.list()).extracting(FeatureToggleView::scopeId).containsExactly("sub_1");
    }
}
