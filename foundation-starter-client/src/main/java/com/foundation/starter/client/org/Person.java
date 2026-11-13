package com.foundation.starter.client.org;

/**
 * 人员（F-07 §8）。
 *
 * @param id              人员 ID（{@code per_…}）
 * @param employeeNo      工号
 * @param name            姓名
 * @param accountId       Foundation 账号 ID，可空
 * @param primaryOrgUnit  主组织单元编码，可空
 * @param status          {@code ACTIVE} / {@code DISABLED}
 */
public record Person(String id, String employeeNo, String name, String accountId, String primaryOrgUnit, String status) {
}
