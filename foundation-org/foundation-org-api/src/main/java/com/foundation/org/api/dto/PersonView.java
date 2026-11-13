package com.foundation.org.api.dto;

/**
 * 人员视图（F-03 §2.1）。
 *
 * <p>{@code mobile} / {@code email} 的取值由调用场景决定：列表返回脱敏值，详情按权限决定明文或脱敏，
 * {@code masked=true} 表示当前返回的是脱敏值（F-03 §4.2）。
 *
 * @param id              人员 ID（{@code per_…}）
 * @param empNo           工号（唯一）
 * @param name            姓名
 * @param type            类型
 * @param status          状态
 * @param mobile          手机（明文或脱敏），无则空
 * @param email           邮箱（明文或脱敏），无则空
 * @param masked          返回值是否已脱敏
 * @param primaryOrgUnitId 主任职所在组织单元 ID，可空
 * @param managerPersonId 直属上级人员 ID，可空
 * @param version         行版本（事件版本）
 */
public record PersonView(String id,
                         String empNo,
                         String name,
                         PersonType type,
                         PersonStatus status,
                         String mobile,
                         String email,
                         boolean masked,
                         String primaryOrgUnitId,
                         String managerPersonId,
                         long version) {
}
