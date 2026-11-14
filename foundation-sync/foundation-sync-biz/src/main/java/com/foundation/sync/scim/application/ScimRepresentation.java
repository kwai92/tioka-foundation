package com.foundation.sync.scim.application;

import com.foundation.sync.domain.LandedRecord;
import com.foundation.sync.scim.domain.ScimConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCIM 资源表示（F-08 BAS-13 §1、§3，RFC 7643）：把落地记录渲染为 SCIM JSON。
 *
 * <p>只回传可安全暴露的属性：{@code id}（本地主键）与 {@code externalId}、{@code userName} /
 * {@code displayName}、{@code active}；邮箱与手机在库内只有哈希，**不回传**。
 */
public final class ScimRepresentation {

    /** 用户资源 schema。 */
    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";

    /** 组资源 schema。 */
    public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    /** 列表响应 schema。 */
    public static final String LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";

    /** 错误响应 schema。 */
    public static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private ScimRepresentation() {
    }

    /** 用户表示。 */
    public static Map<String, Object> user(LandedRecord record) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(USER_SCHEMA));
        body.put("id", record.localId());
        putIfPresent(body, "externalId", record.key().externalId());
        putIfPresent(body, "userName", field(record, "empNo"));
        putIfPresent(body, "displayName", field(record, "name"));
        body.put("active", !record.disabled());
        body.put("meta", meta("User", record));
        return body;
    }

    /** 组（组织单元）表示。 */
    public static Map<String, Object> group(LandedRecord record) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(GROUP_SCHEMA, ScimConstants.ORG_UNIT_EXTENSION));
        body.put("id", record.localId());
        putIfPresent(body, "externalId", record.key().externalId());
        putIfPresent(body, "displayName", field(record, "name"));

        Map<String, Object> extension = new LinkedHashMap<>();
        putIfPresent(extension, "code", field(record, "code"));
        putIfPresent(extension, "type", field(record, "type"));
        putIfPresent(extension, "parent", record.parentExternalId());
        if (!extension.isEmpty()) {
            body.put(ScimConstants.ORG_UNIT_EXTENSION, extension);
        }
        body.put("meta", meta("Group", record));
        return body;
    }

    /** 列表响应（{@code ListResponse}）。 */
    public static Map<String, Object> list(List<Map<String, Object>> resources, int startIndex, int totalResults) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(LIST_SCHEMA));
        body.put("totalResults", totalResults);
        body.put("startIndex", startIndex);
        body.put("itemsPerPage", resources == null ? 0 : resources.size());
        body.put("Resources", resources == null ? new ArrayList<>() : resources);
        return body;
    }

    /** SCIM 错误响应体（F-08 BAS-13 §2）。 */
    public static Map<String, Object> error(int status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(ERROR_SCHEMA));
        body.put("detail", detail);
        body.put("status", String.valueOf(status));
        return body;
    }

    private static Map<String, Object> meta(String resourceType, LandedRecord record) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", resourceType);
        meta.put("version", "W/\"" + record.version() + "\"");
        return meta;
    }

    private static Object field(LandedRecord record, String name) {
        return record.fields().get(name);
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
