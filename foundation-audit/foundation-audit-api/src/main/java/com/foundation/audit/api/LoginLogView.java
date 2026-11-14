package com.foundation.audit.api;

import java.time.Instant;

/**
 * 登录日志视图（F-09 BAS-10 §登录日志）。
 *
 * @param id             日志行 ID（{@code lgn_…}）
 * @param loginName      登录名
 * @param personId       人员 ID
 * @param accountId      账号 ID
 * @param keycloakUserId Keycloak 用户 ID
 * @param success        是否成功
 * @param authMethod     认证方式（归一后）
 * @param clientId       客户端 ID
 * @param failureReason  失败原因
 * @param clientIp       客户端 IP
 * @param userAgent      User-Agent
 * @param ts             发生时刻（UTC）
 */
public record LoginLogView(String id, String loginName, String personId, String accountId, String keycloakUserId,
                           boolean success, String authMethod, String clientId, String failureReason,
                           String clientIp, String userAgent, Instant ts) {
}
