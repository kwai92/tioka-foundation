package com.foundation.org.api.dto;

/**
 * 数字员工档案维护（F-03 §3、§5 {@code PUT /api/fdn/persons/by-external/{source}/{externalId}}）。
 *
 * <p>由 Tioka 用其服务账号调用，{@code externalId} 取 Agent 的 Tioka ID，
 * {@code (source, external_id)} 唯一保证重复上岗幂等。上岗 → 建人员 + 主任职 + 汇报关系；
 * 下岗 → {@code status=DISABLED}。
 *
 * @param empNo         工号（数字员工亦赋工号）
 * @param name          名称
 * @param status        目标状态：{@code ACTIVE} 上岗 / {@code DISABLED} 下岗
 * @param orgUnitId     服务部门组织单元 ID（授权书 {@code departmentId} 对应）
 * @param positionId    岗位 ID，可空
 * @param ownerPersonId 负责人人员 ID（数字员工必填）
 */
public record DigitalEmployeeCmd(String empNo,
                                String name,
                                PersonStatus status,
                                String orgUnitId,
                                String positionId,
                                String ownerPersonId) {
}
