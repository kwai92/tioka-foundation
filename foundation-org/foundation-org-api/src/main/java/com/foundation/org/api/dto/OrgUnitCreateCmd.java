package com.foundation.org.api.dto;

import java.time.Instant;

/**
 * 新建组织单元（F-03 §5 {@code POST /api/fdn/org/units}）。
 *
 * @param code           编码（全局唯一）
 * @param name           名称
 * @param type           类型
 * @param parentId       上级组织单元 ID；空表示根节点（{@code depth=1}）
 * @param sortNo         排序，空取 0
 * @param leaderPersonId 负责人人员 ID，可空
 * @param effectiveAt    生效时间，可空
 */
public record OrgUnitCreateCmd(String code,
                               String name,
                               OrgUnitType type,
                               String parentId,
                               Integer sortNo,
                               String leaderPersonId,
                               Instant effectiveAt) {
}
