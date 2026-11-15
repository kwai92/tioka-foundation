package com.foundation.authz.api;

import java.util.List;

/**
 * 「谁有这个权限」查询视图（F-05 BAS-05 §接口 {@code GET /api/authz/who}）。
 *
 * <p>列出持有指定权限码的角色及其授予对象，供管理台反查权限来源。
 *
 * @param permissionCode 被查询的权限码
 * @param subjects       授予对象列表
 */
public record PermissionSubjectView(String permissionCode, List<Entry> subjects) {

    public PermissionSubjectView {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
    }

    /**
     * 单条授予对象。
     *
     * @param roleCode           角色编码
     * @param subjectType        {@code PERSON} / {@code ORG_UNIT} / {@code POST}
     * @param subjectId          授予对象 ID
     * @param includeDescendants 是否含下级（仅 {@code ORG_UNIT} 有意义）
     * @param dataScope          角色的数据范围
     */
    public record Entry(String roleCode, String subjectType, String subjectId,
                        boolean includeDescendants, RoleDataScopeView dataScope) {
    }
}
