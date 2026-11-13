package com.foundation.org.api.dto;

/**
 * 岗位视图（F-03 §2.1）：可作为角色授予对象（BAS-05 引用）。
 *
 * @param id        岗位 ID（{@code pos_…}）
 * @param code      编码
 * @param name      名称
 * @param orgUnitId 所属组织单元 ID，空表示全局岗位
 * @param status    启用 / 停用
 * @param sortNo    排序
 */
public record PositionView(String id,
                           String code,
                           String name,
                           String orgUnitId,
                           OrgStatus status,
                           int sortNo) {
}
