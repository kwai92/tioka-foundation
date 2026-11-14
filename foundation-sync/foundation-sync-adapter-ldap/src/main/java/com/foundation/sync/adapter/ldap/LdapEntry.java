package com.foundation.sync.adapter.ldap;

import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 来源侧一条 LDAP 记录（F-08 BAS-12）：DN、原始属性、外部 ID、变更号与变更时间。
 *
 * <p>{@link #toOrgRecord()} / {@link #toPersonRecord()} 负责映射为 SPI 的 {@link ExternalRecord}；
 * 字段值保持不变形，内部字段映射由同步引擎的 {@code FieldMapper} 完成。
 *
 * @param dn             对象 DN（同时作为单对象回查时使用的外部 ID）
 * @param attributes     原始属性名 → 首个取值
 * @param externalId     外部 ID（objectGUID 的十六进制 / entryUUID / DN）
 * @param changeNumber   AD {@code uSNChanged}（字符串，按数值比较）
 * @param changedAt      变更时间（{@code whenChanged} / {@code modifyTimestamp}）
 * @param accountDisabled 账号是否停用（AD {@code userAccountControl} 的 ACCOUNTDISABLE 位）
 */
public record LdapEntry(String dn,
                        Map<String, Object> attributes,
                        String externalId,
                        String changeNumber,
                        Instant changedAt,
                        boolean accountDisabled) {

    public LdapEntry {
        attributes = attributes == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** 便捷构造。 */
    public static LdapEntry of(String dn, Map<String, Object> attributes, String externalId,
                               String changeNumber, Instant changedAt, boolean accountDisabled) {
        return new LdapEntry(dn, attributes, externalId, changeNumber, changedAt, accountDisabled);
    }

    /** 映射为组织单元记录。 */
    public ExternalRecord toOrgRecord() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("dn", dn);
        String name = firstNonBlank(attr("ou"), attr("cn"));
        if (name != null) {
            fields.put("name", name);
        }
        String parent = LdapDn.parentDn(dn);
        fields.put("parent_dn", parent);
        fields.put("path", LdapDn.orgPath(dn));
        fields.put("depth", LdapDn.depth(dn));
        return new ExternalRecord(SyncObjectType.ORG_UNIT, externalId, fields,
                blankToNull(parent), false, null);
    }

    /** 映射为人员记录；空值字段不放入。 */
    public ExternalRecord toPersonRecord() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("dn", dn);
        String name = firstNonBlank(attr("cn"), attr("displayName"));
        if (name != null) {
            fields.put("name", name);
        }
        String email = firstNonBlank(attr("mail"));
        if (email != null) {
            fields.put("email", email);
        }
        String mobile = firstNonBlank(attr("mobile"), attr("telephoneNumber"));
        if (mobile != null) {
            fields.put("mobile", mobile);
        }
        String empNo = firstNonBlank(attr("employeeNumber"), attr("employeeID"));
        if (empNo != null) {
            fields.put("emp_no", empNo);
        }
        return new ExternalRecord(SyncObjectType.PERSON, externalId, fields, null, accountDisabled, changedAt);
    }

    private String attr(String name) {
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        return value instanceof String s ? s : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
