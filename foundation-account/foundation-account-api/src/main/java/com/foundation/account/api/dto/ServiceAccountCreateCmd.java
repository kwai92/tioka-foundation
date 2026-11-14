package com.foundation.account.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 新建服务账号入参（F-04 §3、§7 {@code POST /api/service-accounts}）。
 *
 * <p>账号主体为 BAS-03 的 {@code fdn_account}（{@code type=SERVICE}）；客户端由 BAS-04 按应用开通。
 *
 * @param appCode         所属应用编码
 * @param name            名称（同应用内唯一）
 * @param permissionCodes 可用权限码子集（空 = 不收窄）
 * @param ownerPersonId   负责人人员 ID（必填；负责人离职进交接待办）
 * @param expiresAt       过期时间，可空表示不过期
 * @param operator        操作人（审计用）
 */
public record ServiceAccountCreateCmd(String appCode,
                                      String name,
                                      List<String> permissionCodes,
                                      String ownerPersonId,
                                      Instant expiresAt,
                                      String operator) {
}
