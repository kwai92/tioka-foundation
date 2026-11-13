package com.foundation.org.api.dto;

import java.time.LocalDate;

/**
 * 任职写入（F-03 §5 {@code POST /api/fdn/persons/{id}/employments}）。
 *
 * @param orgUnitId  组织单元 ID
 * @param positionId 岗位 ID，可空
 * @param primary    是否主任职；置为主任职时原未结束主任职在同一事务内结束
 * @param startDate  开始日期
 * @param endDate    结束日期，空表示未结束
 */
public record EmploymentCmd(String orgUnitId,
                            String positionId,
                            boolean primary,
                            LocalDate startDate,
                            LocalDate endDate) {
}
