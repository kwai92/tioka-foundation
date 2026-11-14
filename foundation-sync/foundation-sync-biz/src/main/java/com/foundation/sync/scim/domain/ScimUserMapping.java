package com.foundation.sync.scim.domain;

import com.foundation.sync.domain.MappedRecord;
import com.foundation.sync.spi.SyncObjectType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SCIM User → {@code fdn_person} 内部字段映射（F-08 BAS-13 §6 {@code ScimSchemaTest}）。
 *
 * <p>内部字段口径与 F-08 BAS-11 §7 一致（{@code MybatisMasterDataPort} 的 PERSON 内置字段名）：
 * {@code empNo} / {@code name} / {@code type} / {@code status} / {@code emailHash} / {@code mobileHash}。
 *
 * <p>邮箱与手机以 SHA-256 十六进制小写落地（口径同 BAS-02 {@code PersonCipher#hash}），
 * 使平台按 {@code email_hash} / {@code mobile_hash} 去重与关联；SCIM 侧不写明文敏感字段。
 */
public final class ScimUserMapping {

    private ScimUserMapping() {
    }

    /**
     * 映射为内部记录。
     *
     * <p>{@code externalId} 即幂等键；{@code parentExternalId} 对人员无意义（置空）。
     */
    public static MappedRecord toMappedRecord(ScimUser user) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ScimValues.put(fields, "empNo", user.effectiveEmpNo());
        ScimValues.put(fields, "name", user.displayName());
        fields.put("type", ScimConstants.DEFAULT_PERSON_TYPE);
        fields.put("status", user.active() ? ScimConstants.PERSON_ACTIVE : ScimConstants.PERSON_DISABLED);
        ScimValues.put(fields, "emailHash", ScimHash.sha256Hex(user.email()));
        ScimValues.put(fields, "mobileHash", ScimHash.sha256Hex(user.mobile()));
        return new MappedRecord(SyncObjectType.PERSON, user.externalId(), fields, null, false);
    }
}
