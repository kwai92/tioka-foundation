package com.foundation.sync.scim.domain;

import com.foundation.sync.domain.MappedRecord;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-08 BAS-13 §6 {@code ScimSchemaTest}：SCIM User / Group → {@code fdn_person} / {@code fdn_org_unit}
 * 内部字段映射正确，且 {@code externalId} 为幂等键（原样带出）。
 */
class ScimSchemaTest {

    @Test
    void userMapsToPersonFields() {
        ScimUser user = ScimUser.from(Map.of(
                "externalId", "scim-u1",
                "userName", "E1001",
                "displayName", "张三",
                "emails", List.of(Map.of("value", "zhangsan@example.com", "primary", true)),
                "phoneNumbers", List.of(Map.of("value", "13800000000")),
                "active", true));

        MappedRecord mapped = ScimUserMapping.toMappedRecord(user);

        assertThat(mapped.objectType()).isEqualTo(SyncObjectType.PERSON);
        assertThat(mapped.externalId()).isEqualTo("scim-u1");
        assertThat(mapped.fields()).containsEntry("empNo", "E1001")
                .containsEntry("name", "张三")
                .containsEntry("type", "EMPLOYEE")
                .containsEntry("status", "ACTIVE");
        assertThat(mapped.fields().get("emailHash")).isEqualTo(ScimHash.sha256Hex("zhangsan@example.com"));
        assertThat(mapped.fields().get("mobileHash")).isEqualTo(ScimHash.sha256Hex("13800000000"));
        assertThat(mapped.parentExternalId()).isNull();
    }

    @Test
    void inactiveUserMapsToDisabledStatus() {
        ScimUser user = ScimUser.from(Map.of("externalId", "scim-u2", "userName", "E2", "active", false));

        assertThat(ScimUserMapping.toMappedRecord(user).fields()).containsEntry("status", "DISABLED");
    }

    @Test
    void employeeNumberFallsBackToUserName() {
        ScimUser user = ScimUser.from(Map.of("externalId", "scim-u3", "userName", "E3"));

        assertThat(user.effectiveEmpNo()).isEqualTo("E3");
        assertThat(ScimUserMapping.toMappedRecord(user).fields()).containsEntry("empNo", "E3");
    }

    @Test
    void userExtensionCarriesEmployeeNumber() {
        ScimUser user = ScimUser.from(Map.of(
                "externalId", "scim-u4",
                "userName", "zhang.san@example.com",
                ScimConstants.USER_EXTENSION, Map.of("employeeNumber", "E4")));

        assertThat(user.effectiveEmpNo()).isEqualTo("E4");
    }

    @Test
    void groupMapsToOrgUnitFields() {
        ScimGroup group = ScimGroup.from(Map.of(
                "externalId", "scim-g1",
                "displayName", "研发部",
                "type", "DEPARTMENT",
                "parent", "scim-g0",
                "active", true));

        MappedRecord mapped = ScimGroupMapping.toMappedRecord(group);

        assertThat(mapped.objectType()).isEqualTo(SyncObjectType.ORG_UNIT);
        assertThat(mapped.externalId()).isEqualTo("scim-g1");
        assertThat(mapped.parentExternalId()).isEqualTo("scim-g0");
        assertThat(mapped.fields()).containsEntry("code", "scim-g1")
                .containsEntry("name", "研发部")
                .containsEntry("type", "DEPARTMENT")
                .containsEntry("status", "ACTIVE")
                .containsEntry("sortNo", 0);
    }

    @Test
    void groupTypeOutsideWhitelistFallsBackToDepartment() {
        ScimGroup group = ScimGroup.from(Map.of(
                "externalId", "scim-g2",
                "displayName", "未知类型",
                "type", "TEAM"));

        assertThat(ScimGroupMapping.toMappedRecord(group).fields()).containsEntry("type", "DEPARTMENT");
    }

    @Test
    void orgUnitExtensionCarriesTypeParentAndCode() {
        ScimGroup group = ScimGroup.from(Map.of(
                "externalId", "scim-g3",
                "displayName", "公司",
                ScimConstants.ORG_UNIT_EXTENSION, Map.of(
                        "code", "C001",
                        "type", "COMPANY",
                        "parent", "scim-root")));

        assertThat(group.effectiveCode()).isEqualTo("C001");
        assertThat(group.type()).isEqualTo("COMPANY");
        assertThat(group.parentExternalId()).isEqualTo("scim-root");
    }
}
