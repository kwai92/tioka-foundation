package com.foundation.org.domain;

import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.PositionView;

/**
 * 岗位（F-03 §2.1）：可作为角色授予对象（BAS-05 引用）。
 *
 * @param id        主键（{@code pos_…}）
 * @param code      编码（唯一）
 * @param name      名称
 * @param orgUnitId 所属组织单元 ID，空表示全局岗位
 * @param status    启用 / 停用
 * @param sortNo    排序
 * @param source    来源
 * @param externalId 外部 ID
 */
public record Position(String id,
                       String code,
                       String name,
                       String orgUnitId,
                       OrgStatus status,
                       int sortNo,
                       String source,
                       String externalId) {

    /** 转对外视图。 */
    public PositionView toView() {
        return new PositionView(id, code, name, orgUnitId, status, sortNo);
    }
}
