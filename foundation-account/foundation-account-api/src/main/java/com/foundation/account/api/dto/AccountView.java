package com.foundation.account.api.dto;

import java.time.Instant;

/**
 * 账号视图（F-04 §1）。
 *
 * @param id              账号 ID（{@code acc_…}）
 * @param loginName       登录名
 * @param personId        关联人员 ID（应急管理员与服务账号为空）
 * @param keycloakUserId  Keycloak 用户 ID（待发时为空）
 * @param status          状态
 * @param type            类型
 * @param lastLoginAt     最近登录时间，可空
 * @param version         行版本（事件版本）
 */
public record AccountView(String id,
                          String loginName,
                          String personId,
                          String keycloakUserId,
                          AccountStatus status,
                          AccountType type,
                          Instant lastLoginAt,
                          long version) {
}
