package com.foundation.app.api;

import java.time.Instant;
import java.util.List;

/**
 * 服务账号视图（F-05 BAS-04 §领域模型）。
 *
 * @param id              服务账号 ID（{@code svc_{ulid}}）
 * @param accountId       账号主体（BAS-03 的 {@code fdn_account.id}）
 * @param appId           所属应用
 * @param clientId        对应 Keycloak 客户端（{@code client_credentials} 流程）
 * @param name            名称
 * @param permissionCodes 可用权限码白名单
 * @param ownerUserId     负责人
 * @param status          {@code ACTIVE} / {@code DISABLED}
 * @param expiresAt       过期时间，可空
 */
public record ServiceAccountView(String id,
                                 String accountId,
                                 String appId,
                                 String clientId,
                                 String name,
                                 List<String> permissionCodes,
                                 String ownerUserId,
                                 String status,
                                 Instant expiresAt) {
}
