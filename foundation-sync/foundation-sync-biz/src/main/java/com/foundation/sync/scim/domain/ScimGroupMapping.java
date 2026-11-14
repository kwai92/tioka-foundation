package com.foundation.sync.scim.domain;

import com.foundation.sync.domain.MappedRecord;
import com.foundation.sync.spi.SyncObjectType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SCIM Group → {@code fdn_org_unit} 内部字段映射（F-08 BAS-13 §6 {@code ScimSchemaTest}）。
 *
 * <p>内部字段口径与 F-08 BAS-11 §7 一致（{@code MybatisMasterDataPort} 的 ORG_UNIT 内置字段名）：
 * {@code code} / {@code name} / {@code type} / {@code status} / {@code sortNo}；
 * 层级（上级部门）由 {@code parentExternalId} 单独承载（{@code path} / {@code depth} 由落地侧推导，
 * 根为 1 且受 {@code ck_org_unit_depth}（1〜10）约束）。
 */
public final class ScimGroupMapping {

    private ScimGroupMapping() {
    }

    /** 映射为内部记录；{@code externalId} 即幂等键。 */
    public static MappedRecord toMappedRecord(ScimGroup group) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ScimValues.put(fields, "code", group.effectiveCode());
        ScimValues.put(fields, "name", group.displayName());
        fields.put("type", orgUnitType(group.type()));
        fields.put("status", group.active() ? ScimConstants.PERSON_ACTIVE : ScimConstants.PERSON_DISABLED);
        fields.put("sortNo", 0);
        return new MappedRecord(SyncObjectType.ORG_UNIT, group.externalId(), fields,
                group.parentExternalId(), false);
    }

    /** 类型白名单校验（{@code ck_org_unit_type}）；未声明时按部门处理。 */
    private static String orgUnitType(String type) {
        if (type == null) {
            return ScimConstants.DEFAULT_ORG_UNIT_TYPE;
        }
        String upper = type.trim().toUpperCase(java.util.Locale.ROOT);
        return ScimConstants.ORG_UNIT_TYPES.contains(upper) ? upper : ScimConstants.DEFAULT_ORG_UNIT_TYPE;
    }
}
