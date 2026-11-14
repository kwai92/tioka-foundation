package com.foundation.sync.scim.domain;

import java.util.List;
import java.util.Map;

/**
 * SCIM 2.0 User 载荷（F-08 BAS-13 §1、§3，RFC 7643 §4.1）。
 *
 * <p>只保留落地与关联所需的子集：{@code externalId}（幂等键）、{@code userName}（工号或已验证邮箱）、
 * {@code displayName}（姓名）、{@code employeeNumber}（工号，落在 {@code urn:foundation:…:user:2.0}
 * 扩展或顶层同名属性）、{@code emails} / {@code phoneNumbers} 的主值、{@code active}。
 *
 * @param externalId     来源侧对象标识（幂等键，SCIM {@code externalId}）
 * @param userName       登录名，通常为工号或邮箱
 * @param displayName    显示名（姓名）
 * @param employeeNumber 工号（扩展属性，可空）
 * @param email          主邮箱（明文，落地时转 SHA-256 哈希）
 * @param mobile         主手机号（明文，落地时转 SHA-256 哈希）
 * @param active         是否启用（缺省 {@code true}）
 */
public record ScimUser(String externalId,
                       String userName,
                       String displayName,
                       String employeeNumber,
                       String email,
                       String mobile,
                       boolean active) {

    /** 从 SCIM JSON 载荷（Jackson 反序列化的 Map）解析。 */
    public static ScimUser from(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> extension = extension(body, ScimConstants.USER_EXTENSION);
        String employeeNumber = ScimValues.text(body.get("employeeNumber"));
        if (employeeNumber == null) {
            employeeNumber = ScimValues.text(extension.get("employeeNumber"));
        }
        return new ScimUser(
                ScimValues.text(body.get("externalId")),
                ScimValues.text(body.get("userName")),
                displayName(body),
                employeeNumber,
                primary(body, "emails"),
                primary(body, "phoneNumbers"),
                active(body));
    }

    /** 工号口径：显式 {@code employeeNumber} 优先，其次 {@code userName}（常被 IdP 用作工号）。 */
    public String effectiveEmpNo() {
        return employeeNumber != null ? employeeNumber : userName;
    }

    private static String displayName(Map<String, Object> body) {
        String display = ScimValues.text(body.get("displayName"));
        if (display != null) {
            return display;
        }
        Object raw = body.get("name");
        if (raw instanceof Map<?, ?> map) {
            String formatted = ScimValues.text(map.get("formatted"));
            if (formatted != null) {
                return formatted;
            }
            String given = ScimValues.text(map.get("givenName"));
            String family = ScimValues.text(map.get("familyName"));
            String combined = ((family == null ? "" : family) + (given == null ? "" : given));
            return combined.isEmpty() ? null : combined;
        }
        return null;
    }

    static boolean active(Map<String, Object> body) {
        Object value = body.get("active");
        return value == null || Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /** 多值属性取 {@code primary = true} 的项，缺省取第一项。 */
    static String primary(Map<String, Object> body, String attribute) {
        Object raw = body.get(attribute);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        String fallback = null;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String value = ScimValues.text(map.get("value"));
                if (value == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(map.get("primary"))) {
                    return value;
                }
                if (fallback == null) {
                    fallback = value;
                }
            }
        }
        return fallback;
    }

    /** 取扩展 URN 对应的子对象（Jackson 会把扩展解析为同名嵌套 Map）。 */
    static Map<String, Object> extension(Map<String, Object> body, String urn) {
        Object raw = body.get(urn);
        if (raw instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extension = (Map<String, Object>) map;
            return extension;
        }
        return Map.of();
    }
}
