package com.foundation.auth.api;

import java.util.List;

/**
 * 修改密码 / 会话策略的命令（F-04 BAS-15 §4 {@code PUT /api/auth/policy}）。
 *
 * <p>字段可空：为空表示该字段沿用现有值，便于局部修改。
 *
 * @param passwordPolicy     Keycloak passwordPolicy 字符串
 * @param sessionIdleMinutes 会话空闲超时（分钟）
 * @param sessionMaxHours    会话最长有效（小时）
 * @param concurrentSessions 并发会话上限（{@code 0} 表示不限）
 * @param totpRequiredRoles  强制 TOTP 的角色编码列表
 */
public record AuthPolicyCmd(String passwordPolicy, Integer sessionIdleMinutes, Integer sessionMaxHours,
                            Integer concurrentSessions, List<String> totpRequiredRoles) {
}
