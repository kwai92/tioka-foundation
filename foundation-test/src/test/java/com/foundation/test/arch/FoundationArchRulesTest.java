package com.foundation.test.arch;

import com.foundation.test.fixture.application.SynchronizedService;
import com.foundation.test.fixture.catalog.FixturePermissionCodes;
import com.foundation.test.fixture.controller.CleanController;
import com.foundation.test.fixture.controller.OffCatalogController;
import com.foundation.test.fixture.controller.UnannotatedController;
import com.foundation.test.fixture.external.ControllerCallingExternalSystem;
import com.foundation.test.fixture.jdbc.JdbcTemplateFixture;
import com.foundation.test.fixture.json.ObjectMapperFixture;
import com.foundation.test.fixture.modulea.api.AlphaApi;
import com.foundation.test.fixture.modulea.application.AlphaService;
import com.foundation.test.fixture.moduleb.application.BetaService;
import com.foundation.test.fixture.moduleb.application.CleanBetaService;
import com.foundation.test.fixture.persistence.InstantTimeFieldDO;
import com.foundation.test.fixture.persistence.LocalDateTimeDO;
import com.foundation.test.fixture.tenant.SysTenantDO;
import com.foundation.test.fixture.tenant.TenantFieldFixture;
import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 规则集单测：为每条规则提供正/反用例（只导入该条规则涉及的夹具）。
 *
 * <p>{@link ArchConfiguration} 是 JVM 级单例，规则 4 的用例在每个方法开头显式设定目录属性，
 * 不依赖执行顺序。
 */
class FoundationArchRulesTest {

    private static JavaClasses importOf(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    // ------------------------------------------------------------------
    // 规则 1：@RestController 方法必须声明权限或 @PublicApi
    // ------------------------------------------------------------------

    @Test
    void controllersDeclarePermissionOrPublicApi_passesForAnnotatedController() {
        FoundationArchRules.controllersDeclarePermissionOrPublicApi.check(importOf(CleanController.class));
    }

    @Test
    void controllersDeclarePermissionOrPublicApi_reportsUnannotatedMethod() {
        assertThatThrownBy(() -> FoundationArchRules.controllersDeclarePermissionOrPublicApi
                .check(importOf(CleanController.class, UnannotatedController.class)))
                .isInstanceOf(AssertionError.class);
    }

    // ------------------------------------------------------------------
    // 规则 2：请求线程禁止同步调用外部系统
    // ------------------------------------------------------------------

    @Test
    void noSyncExternalCallInRequestThread_passesForCleanController() {
        FoundationArchRules.noSyncExternalCallInRequestThread.check(importOf(CleanController.class));
    }

    @Test
    void noSyncExternalCallInRequestThread_reportsControllerCallingExternalSystem() {
        assertThatThrownBy(() -> FoundationArchRules.noSyncExternalCallInRequestThread
                .check(importOf(ControllerCallingExternalSystem.class)))
                .isInstanceOf(AssertionError.class);
    }

    // ------------------------------------------------------------------
    // 规则 3：禁止 tenant_id / sys_tenant
    // ------------------------------------------------------------------

    @Test
    void noTenantId_reportsTenantIdField() {
        assertThatThrownBy(() -> FoundationArchRules.noTenantId.check(importOf(TenantFieldFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noTenantId_reportsSysTenantClass() {
        assertThatThrownBy(() -> FoundationArchRules.noTenantId.check(importOf(SysTenantDO.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noTenantId_passesForCleanController() {
        FoundationArchRules.noTenantId.check(importOf(CleanController.class));
    }

    // ------------------------------------------------------------------
    // 规则 4：权限码只能来自常量目录
    // ------------------------------------------------------------------

    @Test
    void permissionCodesFromCatalog_passesWhenCodeInCatalog() {
        ArchConfiguration.get().setProperty(FoundationArchRules.PERMISSION_CATALOG_PROPERTY,
                FixturePermissionCodes.class.getName());
        FoundationArchRules.permissionCodesFromCatalog.check(importOf(CleanController.class));
    }

    @Test
    void permissionCodesFromCatalog_reportsOffCatalogCode() {
        ArchConfiguration.get().setProperty(FoundationArchRules.PERMISSION_CATALOG_PROPERTY,
                FixturePermissionCodes.class.getName());
        assertThatThrownBy(() -> FoundationArchRules.permissionCodesFromCatalog
                .check(importOf(OffCatalogController.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void permissionCodesFromCatalog_reportsWhenCatalogNotConfigured() {
        ArchConfiguration.get().setProperty(FoundationArchRules.PERMISSION_CATALOG_PROPERTY, "");
        assertThatThrownBy(() -> FoundationArchRules.permissionCodesFromCatalog
                .check(importOf(CleanController.class)))
                .isInstanceOf(AssertionError.class);
    }

    // ------------------------------------------------------------------
    // 规则 5：跨模块只依赖对方 -api
    // ------------------------------------------------------------------

    @Test
    void modulesDependOnlyOnApi_reportsCrossModuleInternalDependency() {
        assertThatThrownBy(() -> FoundationArchRules.modulesDependOnlyOnApi
                .check(importOf(BetaService.class, AlphaService.class, AlphaApi.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void modulesDependOnlyOnApi_passesForApiOnlyAndSameModuleDependencies() {
        FoundationArchRules.modulesDependOnlyOnApi
                .check(importOf(CleanBetaService.class, AlphaApi.class, AlphaService.class));
    }

    // ------------------------------------------------------------------
    // 规则 6：禁止 new ObjectMapper()
    // ------------------------------------------------------------------

    @Test
    void noNewObjectMapper_reportsNewObjectMapper() {
        assertThatThrownBy(() -> FoundationArchRules.noNewObjectMapper.check(importOf(ObjectMapperFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noNewObjectMapper_passesForCleanController() {
        FoundationArchRules.noNewObjectMapper.check(importOf(CleanController.class));
    }

    // ------------------------------------------------------------------
    // 规则 7：DO 时间字段只用 LocalDateTime
    // ------------------------------------------------------------------

    @Test
    void doTimeFieldsAreLocalDateTime_reportsInstantField() {
        assertThatThrownBy(() -> FoundationArchRules.doTimeFieldsAreLocalDateTime
                .check(importOf(InstantTimeFieldDO.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void doTimeFieldsAreLocalDateTime_passesForLocalDateTimeField() {
        FoundationArchRules.doTimeFieldsAreLocalDateTime.check(importOf(LocalDateTimeDO.class));
    }

    // ------------------------------------------------------------------
    // 规则 8：应用/领域层禁止 synchronized / ReentrantLock
    // ------------------------------------------------------------------

    @Test
    void noSynchronizedOrReentrantLock_reportsSynchronizedAndLock() {
        assertThatThrownBy(() -> FoundationArchRules.noSynchronizedOrReentrantLock
                .check(importOf(SynchronizedService.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noSynchronizedOrReentrantLock_passesForCleanApplicationService() {
        FoundationArchRules.noSynchronizedOrReentrantLock.check(importOf(CleanBetaService.class));
    }

    // ------------------------------------------------------------------
    // 规则 9：业务代码禁用 JdbcTemplate
    // ------------------------------------------------------------------

    @Test
    void noJdbcTemplateInBusinessCode_reportsJdbcTemplateUsage() {
        assertThatThrownBy(() -> FoundationArchRules.noJdbcTemplateInBusinessCode
                .check(importOf(JdbcTemplateFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noJdbcTemplateInBusinessCode_passesForCleanApplicationService() {
        FoundationArchRules.noJdbcTemplateInBusinessCode.check(importOf(CleanBetaService.class));
    }
}
