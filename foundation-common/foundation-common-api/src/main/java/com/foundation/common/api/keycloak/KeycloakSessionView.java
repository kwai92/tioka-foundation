package com.foundation.common.api.keycloak;

/**
 * Keycloak 在线会话表示（F-04 BAS-03 §7 账号在线会话入口）。
 *
 * @param id         会话 ID
 * @param userId     Keycloak 用户 ID
 * @param username   登录名
 * @param ipAddress  客户端 IP
 * @param start      会话开始时间（epoch 毫秒）
 * @param lastAccess 最近访问时间（epoch 毫秒）
 */
public record KeycloakSessionView(String id,
                                  String userId,
                                  String username,
                                  String ipAddress,
                                  long start,
                                  long lastAccess) {
}
