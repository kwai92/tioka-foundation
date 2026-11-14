package com.foundation.sync.scim.domain;

import java.util.Map;

/**
 * SCIM 2.0 Group 载荷（F-08 BAS-13 §1、§3，RFC 7643 §4.2）。
 *
 * <p>SCIM Group 映射到**有层级的** {@code fdn_org_unit}（部门作为组）。层级与类型来自
 * {@code urn:foundation:params:scim:schemas:extension:orgUnit:2.0} 扩展（或顶层同名属性）。
 *
 * @param externalId       来源侧对象标识（幂等键）
 * @param displayName      组显示名（组织单元名称）
 * @param code             组织单元编码（缺省取 {@code externalId}）
 * @param type             组织单元类型（{@code GROUP} / {@code COMPANY} / {@code DEPARTMENT}）
 * @param active           是否启用（缺省 {@code true}）
 * @param parentExternalId 上级组的外部 ID（建树），可空表示根
 */
public record ScimGroup(String externalId,
                        String displayName,
                        String code,
                        String type,
                        boolean active,
                        String parentExternalId) {

    /** 从 SCIM JSON 载荷（Jackson 反序列化的 Map）解析。 */
    public static ScimGroup from(Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Map<String, Object> extension = ScimUser.extension(body, ScimConstants.ORG_UNIT_EXTENSION);
        String parent = ScimValues.text(body.get("parent"));
        if (parent == null) {
            parent = ScimValues.text(extension.get("parent"));
        }
        String type = ScimValues.text(body.get("type"));
        if (type == null) {
            type = ScimValues.text(extension.get("type"));
        }
        String code = ScimValues.text(body.get("code"));
        if (code == null) {
            code = ScimValues.text(extension.get("code"));
        }
        return new ScimGroup(
                ScimValues.text(body.get("externalId")),
                ScimValues.text(body.get("displayName")),
                code,
                type,
                ScimUser.active(body),
                parent);
    }

    /** 编码口径：显式 {@code code} 优先，否则回落到 {@code externalId}（{@code code} 全局唯一）。 */
    public String effectiveCode() {
        return code != null ? code : externalId;
    }
}
