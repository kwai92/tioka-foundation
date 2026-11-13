package com.foundation.org.api.dto;

import java.time.Instant;

/**
 * 组织单元视图（F-03 §2.1）。
 *
 * @param id             组织单元 ID（{@code org_…}）
 * @param code           编码（全局唯一）
 * @param name           名称
 * @param type           类型
 * @param parentId       上级组织单元 ID，根为空
 * @param path           物化路径（{@code /org_a/org_b}），供子树查询
 * @param depth          深度，根为 1，≤ 10
 * @param sortNo         排序
 * @param leaderPersonId 负责人人员 ID，可空
 * @param effectiveAt    生效时间，可空
 * @param status         启用 / 停用
 * @param version        行版本（事件版本）
 */
public record OrgUnitView(String id,
                          String code,
                          String name,
                          OrgUnitType type,
                          String parentId,
                          String path,
                          int depth,
                          int sortNo,
                          String leaderPersonId,
                          Instant effectiveAt,
                          OrgStatus status,
                          long version) {
}
