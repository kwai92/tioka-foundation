package com.foundation.authz.application;

import com.foundation.authz.api.DirectoryNodeView;
import com.foundation.authz.api.DirectoryView;
import com.foundation.authz.api.SubsystemView;
import com.foundation.authz.config.SubsystemProperties;
import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.SelfImportStatus;
import com.foundation.authz.domain.SelfManagedImport;
import com.foundation.authz.domain.Subsystem;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 子系统与目录（F-05 BAS-27 §目录 / §四种授权模式）。 */
class SubsystemDirectoryServiceTest {

    private static final String APP = "app_a";

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemorySubsystemRepository subsystems =
            new TestDoubles.InMemorySubsystemRepository();
    private final TestDoubles.InMemoryModuleRepository modules = new TestDoubles.InMemoryModuleRepository();
    private final TestDoubles.InMemoryFunctionRepository functions =
            new TestDoubles.InMemoryFunctionRepository();
    private final TestDoubles.InMemorySelfManagedImportRepository imports =
            new TestDoubles.InMemorySelfManagedImportRepository();
    private final TestDoubles.InMemoryMenuRepository menus = new TestDoubles.InMemoryMenuRepository();
    private final TestDoubles.StubFeatureToggleApi toggles = new TestDoubles.StubFeatureToggleApi();
    private final SubsystemProperties properties = new SubsystemProperties();

    private SubsystemDirectoryService service;

    @BeforeEach
    void setUp() {
        apps.register("app_a", APP);
        subsystems.insert(new Subsystem("sub_1", "app_a", AuthMode.CONCENTRATED, true, null));
        modules.insert(new SubsystemModule("mod_1", "sub_1", "workflow", "工作流", 0, "ACTIVE"));
        functions.insert(new SubsystemFunction("fun_1", "mod_1", "wf.flow", "流程管理", "men_flow", 0,
                "ACTIVE"));
        menus.insert(menu("men_flow", "flow", "MENU", null, null));
        menus.insert(menu("men_submit", "submit", "BUTTON", "men_flow", "c:submit"));

        service = new SubsystemDirectoryService(subsystems, modules, functions, imports, menus, toggles,
                apps, new AppIdResolver(apps), properties);
    }

    @Test
    void directoryBuildsModuleFunctionOperationTree() {
        DirectoryView view = service.directory(APP);

        assertThat(view.appCode()).isEqualTo(APP);
        assertThat(view.authMode()).isEqualTo("CONCENTRATED");
        assertThat(view.modules()).hasSize(1);

        DirectoryNodeView module = view.modules().get(0);
        assertThat(module.level()).isEqualTo("MODULE");
        assertThat(module.code()).isEqualTo("workflow");
        assertThat(module.enabled()).isTrue();

        DirectoryNodeView function = module.children().get(0);
        assertThat(function.level()).isEqualTo("FUNCTION");
        assertThat(function.menuId()).isEqualTo("men_flow");
        assertThat(function.children()).extracting(DirectoryNodeView::level).containsExactly("OPERATION");
        assertThat(function.children().get(0).code()).isEqualTo("c:submit");
    }

    @Test
    void disabledModuleAndFunctionMarkNodesDisabled() {
        toggles.disable("MODULE", "mod_1");
        toggles.disable("FUNCTION", "fun_1");

        DirectoryView view = service.directory(APP);

        assertThat(view.modules().get(0).enabled()).isFalse();
        assertThat(view.modules().get(0).children().get(0).enabled()).isFalse();
        assertThat(view.modules().get(0).children().get(0).children().get(0).enabled()).isFalse();
    }

    @Test
    void offlineModulesAndFunctionsAreNotListed() {
        modules.insert(new SubsystemModule("mod_2", "sub_1", "offline.mod", "已下线", 1, "OFFLINE"));
        functions.insert(new SubsystemFunction("fun_2", "mod_1", "wf.old", "旧功能", null, 1, "OFFLINE"));

        DirectoryView view = service.directory(APP);

        assertThat(view.modules()).extracting(DirectoryNodeView::code).containsExactly("workflow");
        assertThat(view.modules().get(0).children()).extracting(DirectoryNodeView::code)
                .containsExactly("wf.flow");
    }

    @Test
    void selfToConcentratedRequiresAppliedImport() {
        subsystems.insert(new Subsystem("sub_1", "app_a", AuthMode.SELF, true, null));

        assertThatThrownBy(() -> service.changeAuthMode(APP, "CONCENTRATED", "admin"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        imports.insert(new SelfManagedImport("mpx_1", "sub_1", SelfImportStatus.APPLIED, null));

        SubsystemView view = service.changeAuthMode(APP, "CONCENTRATED", "admin");

        assertThat(view.authMode()).isEqualTo("CONCENTRATED");
        assertThat(subsystems.findById("sub_1").orElseThrow().authMode()).isEqualTo(AuthMode.CONCENTRATED);
    }

    @Test
    void findProvisionsSubsystemWithDefaultAuthMode() {
        subsystems.byId.clear();

        DirectoryView view = service.directory(APP);

        assertThat(view.authMode()).isEqualTo(properties.getDefaultAuthMode());
        assertThat(subsystems.findByAppId("app_a")).isPresent();
    }

    @Test
    void listCarriesAppCodeAndName() {
        List<SubsystemView> views = service.list();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).appCode()).isEqualTo(APP);
        assertThat(views.get(0).name()).isEqualTo(APP);
    }

    private static Menu menu(String id, String code, String type, String parentId, String permissionCode) {
        return new Menu(id, "app_a", code, code, type, parentId, null, null, null, 0, true, permissionCode,
                LifecycleStatus.ACTIVE);
    }
}
