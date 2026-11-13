package com.foundation.app.api;

import java.time.Instant;
import java.util.List;

/**
 * 服务账号登记命令（F-05 BAS-04 §接口 {@code POST /api/apps/{code}/service-accounts}）。
 *
 * @param appCode         所属应用编码
 * @param accountId       账号主体 ID（BAS-03 的 {@code fdn_account.id}，{@code type=SERVICE}）
 * @param name            名称（应用内唯一）
 * @param permissionCodes 可用权限码白名单；最终权限 = 快照权限 ∩ 该白名单
 * @param ownerUserId     负责人
 * @param expiresAt       过期时间，可空（空表示不过期）
 * @param operator        操作人
 */
public record ServiceAccountCmd(String appCode,
                                String accountId,
                                String name,
                                List<String> permissionCodes,
                                String ownerUserId,
                                Instant expiresAt,
                                String operator) {
}
