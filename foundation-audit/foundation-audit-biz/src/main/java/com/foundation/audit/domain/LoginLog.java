package com.foundation.audit.domain;

import com.foundation.audit.api.AuthMethods;
import com.foundation.common.api.keycloak.KeycloakLoginEvent;
import com.foundation.common.id.Ids;

import java.time.Instant;

/**
 * 登录日志行（F-09 BAS-10 §登录日志）：来自 Keycloak 的 LOGIN / LOGIN_ERROR 事件。
 *
 * @param id             主键（{@code lgn_…}）
 * @param eventId        Keycloak 事件 ID（幂等键）
 * @param accountId      Foundation 账号 ID，可空（回填前为空）
 * @param loginName      登录名，可空
 * @param personId       人员 ID，可空（回填前为空）
 * @param keycloakUserId Keycloak 用户 ID，可空
 * @param success        是否成功
 * @param authMethod     认证方式（归一后）
 * @param clientId       客户端 ID，可空
 * @param failureReason  失败原因，可空
 * @param clientIp       客户端 IP，可空
 * @param userAgent      User-Agent，可空
 * @param ts             发生时刻（UTC）
 */
public record LoginLog(String id,
                       String eventId,
                       String accountId,
                       String loginName,
                       String personId,
                       String keycloakUserId,
                       boolean success,
                       String authMethod,
                       String clientId,
                       String failureReason,
                       String clientIp,
                       String userAgent,
                       Instant ts) {

    /** 由 Keycloak 登录事件构造（账号信息待 {@link #withAccount} 回填）。 */
    public static LoginLog fromEvent(KeycloakLoginEvent event) {
        return new LoginLog(Ids.newId(AuditIdPrefix.LOGIN), event.eventId(), null, event.username(), null,
                event.userId(), KeycloakLoginEvent.TYPE_LOGIN.equals(event.type()),
                AuthMethods.normalize(event.authMethod()), event.clientId(), event.error(),
                event.ipAddress(), null, event.occurredAt());
    }

    /** 补齐账号信息（采集时按登录名查到账号后回填）。 */
    public LoginLog withAccount(String newAccountId, String newPersonId) {
        return new LoginLog(id, eventId, newAccountId, loginName, newPersonId, keycloakUserId, success,
                authMethod, clientId, failureReason, clientIp, userAgent, ts);
    }
}
