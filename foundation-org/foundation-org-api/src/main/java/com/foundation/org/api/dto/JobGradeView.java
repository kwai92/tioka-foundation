package com.foundation.org.api.dto;

/**
 * 职级视图（F-03 §2.1）：只做属性，供数据规则与策略引用。
 *
 * @param id         职级 ID（{@code grd_…}）
 * @param code       编码
 * @param name       名称
 * @param series     序列，如 {@code M}（管理）/ {@code P}（专业）
 * @param gradeLevel 等级
 * @param sortNo     排序
 */
public record JobGradeView(String id,
                           String code,
                           String name,
                           String series,
                           int gradeLevel,
                           int sortNo) {
}
