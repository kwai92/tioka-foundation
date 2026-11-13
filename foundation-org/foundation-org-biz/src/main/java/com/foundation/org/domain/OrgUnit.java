package com.foundation.org.domain;

import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.OrgUnitView;

import java.time.Instant;

/**
 * 组织单元（F-03 §2.1）。
 *
 * <p>树形通过 {@code parentId} + 物化路径 {@code path} + {@code depth} 表达：根 {@code depth=1}，
 * 写入时校验不超过 {@code foundation.org.max-tree-depth} 并拒绝成环；{@code path} 供「本部门及以下」
 * 免递归查询（BAS-06）。
 *
 * @param id             主键（{@code org_…}）
 * @param code           编码（全局唯一）
 * @param name           名称
 * @param type           类型
 * @param parentId       上级 ID，根为空
 * @param path           物化路径
 * @param depth          深度，根为 1
 * @param sortNo         排序
 * @param leaderPersonId 负责人人员 ID
 * @param effectiveAt    生效时间
 * @param status         启用 / 停用
 * @param source         来源
 * @param externalId     外部 ID
 * @param version        行版本
 */
public record OrgUnit(String id,
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
                      String source,
                      String externalId,
                      long version) {

    /** 转对外视图。 */
    public OrgUnitView toView() {
        return new OrgUnitView(id, code, name, type, parentId, path, depth, sortNo, leaderPersonId,
                effectiveAt, status, version);
    }

    /** 是否启用。 */
    public boolean active() {
        return status == OrgStatus.ACTIVE;
    }
}
