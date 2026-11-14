package com.foundation.account.api.dto;

import java.time.Instant;

/**
 * 账号在线会话视图（F-04 §7 账号在线会话入口，来自 Keycloak）。
 *
 * @param id         会话 ID
 * @param ipAddress  客户端 IP
 * @param start      会话开始时间
 * @param lastAccess 最近访问时间
 */
public record AccountSessionView(String id, String ipAddress, Instant start, Instant lastAccess) {
}
