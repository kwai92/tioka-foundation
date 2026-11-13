package com.foundation.org.api.dto;

import java.time.Instant;

/**
 * 修改 / 移动组织单元（F-03 §5 {@code PUT /api/fdn/org/units/{id}}）。
 *
 * <p>{@code parentId} 与当前不同即视为「移动」，{@code path} / {@code depth} 及子树级联更新，
 * 并发出 {@code MOVED} 变更类型。
 *
 * @param name           名称
 * @param type           类型
 * @param parentId       上级组织单元 ID，空表示移为根
 * @param sortNo         排序，空表示不变
 * @param leaderPersonId 负责人人员 ID，空表示不变（传空串表示清除）
 * @param effectiveAt    生效时间，空表示不变
 */
public record OrgUnitUpdateCmd(String name,
                               OrgUnitType type,
                               String parentId,
                               Integer sortNo,
                               String leaderPersonId,
                               Instant effectiveAt) {
}
