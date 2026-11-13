package com.foundation.org.domain;

import com.foundation.org.api.dto.EmploymentView;

import java.time.LocalDate;

/**
 * 任职（F-03 §2.1）：一人恰有一个未结束主任职，兼职用多条任职表达。
 *
 * @param id         主键（{@code emp_…}）
 * @param personId   人员 ID
 * @param orgUnitId  组织单元 ID
 * @param positionId 岗位 ID，可空
 * @param primary    是否主任职
 * @param startDate  开始日期
 * @param endDate    结束日期，空表示未结束
 */
public record Employment(String id,
                         String personId,
                         String orgUnitId,
                         String positionId,
                         boolean primary,
                         LocalDate startDate,
                         LocalDate endDate) {

    /** 是否未结束。 */
    public boolean open() {
        return endDate == null;
    }

    /** 转对外视图。 */
    public EmploymentView toView() {
        return new EmploymentView(id, personId, orgUnitId, positionId, primary, startDate, endDate);
    }

    /** 结束该任职（保留历史，不物理删除，48-CS §4）。 */
    public Employment closed(LocalDate endDate) {
        return new Employment(id, personId, orgUnitId, positionId, false, startDate, endDate);
    }
}
