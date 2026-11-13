package com.foundation.org.api.dto;

import java.time.LocalDate;

/**
 * 任职视图（F-03 §2.1）：一人恰有一个未结束主任职，兼职用多条任职表达。
 *
 * @param id         任职 ID（{@code emp_…}）
 * @param personId   人员 ID
 * @param orgUnitId  组织单元 ID
 * @param positionId 岗位 ID，可空
 * @param primary    是否主任职
 * @param startDate  开始日期
 * @param endDate    结束日期，空表示未结束
 */
public record EmploymentView(String id,
                             String personId,
                             String orgUnitId,
                             String positionId,
                             boolean primary,
                             LocalDate startDate,
                             LocalDate endDate) {
}
