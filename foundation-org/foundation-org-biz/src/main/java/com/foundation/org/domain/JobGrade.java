package com.foundation.org.domain;

import com.foundation.org.api.dto.JobGradeView;

/**
 * 职级（F-03 §2.1）：只做属性，供数据规则与策略引用。
 *
 * @param id         主键（{@code grd_…}）
 * @param code       编码（唯一）
 * @param name       名称
 * @param series     序列，如 {@code M}（管理）/ {@code P}（专业）
 * @param gradeLevel 等级
 * @param sortNo     排序
 */
public record JobGrade(String id,
                       String code,
                       String name,
                       String series,
                       int gradeLevel,
                       int sortNo) {

    /** 转对外视图。 */
    public JobGradeView toView() {
        return new JobGradeView(id, code, name, series, gradeLevel, sortNo);
    }
}
