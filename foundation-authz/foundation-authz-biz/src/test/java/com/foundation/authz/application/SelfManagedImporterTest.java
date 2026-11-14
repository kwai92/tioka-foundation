package com.foundation.authz.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.authz.api.SelfImportApplyResult;
import com.foundation.authz.api.SelfImportItemCmd;
import com.foundation.authz.api.SelfImportPreviewView;
import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleScope;
import com.foundation.authz.domain.SelfImportStatus;
import com.foundation.authz.domain.Subsystem;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 自管子系统→集中的一次性导入（F-05 BAS-27 §自管导入）。 */
class SelfManagedImporterTest {

    private static final String APP = "app_a";
    private static final String USER = "user_9";

    private final TestDoubles.InMemoryAppApi apps = new TestDoubles.InMemoryAppApi();
    private final TestDoubles.InMemorySubsystemRepository subsystems =
            new TestDoubles.InMemorySubsystemRepository();
    private final TestDoubles.InMemorySelfManagedImportRepository imports =
            new TestDoubles.InMemorySelfManagedImportRepository();
    private final TestDoubles.InMemoryRoleRepository roles = new TestDoubles.InMemoryRoleRepository();
    private final TestDoubles.InMemoryRoleGrantRepository grants =
            new TestDoubles.InMemoryRoleGrantRepository();

    private SelfManagedImporter importer;

    @BeforeEach
    void setUp() {
        apps.register("app_a", APP);
        subsystems.insert(new Subsystem("sub_1", "app_a", AuthMode.SELF, true, null));
        importer = new SelfManagedImporter(subsystems, imports, roles, grants, new AppIdResolver(apps),
                new ObjectMapper());
    }

    private static List<SelfImportItemCmd> items() {
        return List.of(
                new SelfImportItemCmd("ROLE", "ext.admin", "{\"name\":\"外部管理员\"}"),
                new SelfImportItemCmd("ASSIGNMENT", "ext.admin@" + USER,
                        "{\"roleCode\":\"ext.admin\",\"userId\":\"" + USER + "\"}"));
    }

    @Test
    void previewReportsDiffButDoesNotWriteRolesOrGrants() {
        SelfImportPreviewView view = importer.preview(APP, items(), "admin");

        assertThat(view.status()).isEqualTo(SelfImportStatus.PREVIEW.name());
        assertThat(view.newRoles()).isEqualTo(1);
        assertThat(view.newAssignments()).isEqualTo(1);
        assertThat(view.conflicts()).isZero();

        assertThat(roles.list()).isEmpty();
        assertThat(grants.list(null, null)).isEmpty();
        assertThat(imports.items(view.batchId())).hasSize(2);
        assertThat(imports.findById(view.batchId()).orElseThrow().status())
                .isEqualTo(SelfImportStatus.PREVIEW);
    }

    @Test
    void previewCountsExistingRoleAsConflict() {
        roles.insert(new Role("ext.admin", "外部管理员", RoleScope.APP, "app_a", false, null, Set.of()));

        SelfImportPreviewView view = importer.preview(APP, items(), "admin");

        assertThat(view.newRoles()).isZero();
        assertThat(view.conflicts()).isEqualTo(1);
        assertThat(view.details()).anyMatch(detail -> detail.contains("ext.admin"));
    }

    @Test
    void previewFailsWhenSubsystemMissing() {
        subsystems.byId.clear();

        assertThatThrownBy(() -> importer.preview(APP, items(), "admin"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void applyCreatesRolesAndGrantsAndSwitchesAuthMode() {
        String batchId = importer.preview(APP, items(), "admin").batchId();

        SelfImportApplyResult result = importer.apply(batchId, "admin");

        assertThat(result.alreadyApplied()).isFalse();
        assertThat(result.createdRoles()).isEqualTo(1);
        assertThat(result.createdAssignments()).isEqualTo(1);
        assertThat(result.authMode()).isEqualTo(AuthMode.CONCENTRATED.name());

        assertThat(roles.find("ext.admin")).isPresent();
        assertThat(grants.list(GrantSubjectType.PERSON, USER)).hasSize(1);
        assertThat(subsystems.findById("sub_1").orElseThrow().authMode()).isEqualTo(AuthMode.CONCENTRATED);
        assertThat(imports.findById(batchId).orElseThrow().status()).isEqualTo(SelfImportStatus.APPLIED);
    }

    @Test
    void applyIsIdempotent() {
        String batchId = importer.preview(APP, items(), "admin").batchId();
        importer.apply(batchId, "admin");

        SelfImportApplyResult second = importer.apply(batchId, "admin");

        assertThat(second.alreadyApplied()).isTrue();
        assertThat(second.createdRoles()).isZero();
        assertThat(second.createdAssignments()).isZero();
        assertThat(roles.list()).hasSize(1);
        assertThat(grants.list(GrantSubjectType.PERSON, USER)).hasSize(1);
    }

    @Test
    void applyFailsForUnknownBatch() {
        assertThatThrownBy(() -> importer.apply("mpx_missing", "admin"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
