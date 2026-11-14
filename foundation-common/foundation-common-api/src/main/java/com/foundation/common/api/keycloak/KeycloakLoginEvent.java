package com.foundation.common.api.keycloak;

import java.time.Instant;

/**
 * Keycloak 登录事件（F-09 BAS-10 §登录日志）：从 Keycloak Admin 事件接口拉取的 LOGIN / LOGIN_ERROR 事件。
 *
 * <p>跨模块只依赖 {@code -api}：审计领域只依赖本记录，实现落在 {@code foundation-common-biz} 的 Keycloak 客户端内。
 *
 * @param eventId     事件 ID（Keycloak 事件 {@code id}，用于幂等）
 * @param type        {@link #TYPE_LOGIN} 或 {@link #TYPE_LOGIN_ERROR}
 * @param clientId    客户端 ID，可空
 * @param userId      Keycloak 用户 ID，可空
 * @param username    登录名（取自 {@code details.username}），可空
 * @param error       失败原因（取自事件 {@code error}），可空
 * @param authMethod  认证方式（取自 {@code details.auth_method}），可空
 * @param ipAddress   客户端 IP，可空
 * @param occurredAt  发生时刻（UTC）
 */
public record KeycloakLoginEvent(String eventId, String type, String clientId, String userId, String username,
                                 String error, String authMethod, String ipAddress, Instant occurredAt) {

    /** 登录成功事件类型。 */
    public static final String TYPE_LOGIN = "LOGIN";
    /** 登录失败事件类型。 */
    public static final String TYPE_LOGIN_ERROR = "LOGIN_ERROR";
}
