package com.foundation.notify.api;

/** 公告范围（F-09 BAS-16 §数据模型 {@code fdn_announcement.scope_type}）。 */
public enum AnnouncementScopeType {

    /** 全员。 */
    GLOBAL,
    /** 组织单元（含子树）。 */
    ORG,
    /** 角色。 */
    ROLE;

    /** 解析；无法识别返回 {@code null}。 */
    public static AnnouncementScopeType of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AnnouncementScopeType scope : values()) {
            if (scope.name().equalsIgnoreCase(value.trim())) {
                return scope;
            }
        }
        return null;
    }
}
