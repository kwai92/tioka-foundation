package com.foundation.authz.api;

import java.time.Instant;

/**
 * 授权命令（F-05 BAS-05 §领域模型 {@code RoleGrant}）。
 *
 * @param roleCode           角色编码
 * @param subjectType        {@code PERSON} 人员 / {@code ORG_UNIT} 组织单元 / {@code POST} 岗位
 * @param subjectId          授予对象 ID
 * @param includeDescendants 是否含下级（仅 {@code ORG_UNIT} 有意义）
 * @param effectiveFrom      生效时间，{@code null} 表示不限
 * @param effectiveTo        失效时间，{@code null} 表示不限
 * @param source             来源 {@code MANUAL} / {@code RULE} / {@code SYNC}
 * @param operator           操作人
 */
public record RoleGrantCmd(String roleCode,
                           String subjectType,
                           String subjectId,
                           boolean includeDescendants,
                           Instant effectiveFrom,
                           Instant effectiveTo,
                           String source,
                           String operator) {
}
