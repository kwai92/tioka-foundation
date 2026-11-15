package com.foundation.auth.api;

import java.util.List;

/**
 * 密码 / 会话策略视图（F-04 BAS-15 §2 密码策略与会话策略）。
 *
 * @param passwordPolicy       Keycloak passwordPolicy 字符串，如 {@code length(8) and digits(1) and upperCase(1)}
 * @param sessionIdleMinutes   SSO 会话空闲超时（分钟）
 * @param sessionMaxHours      SSO 会话最长有效（小时）
 * @param concurrentSessions   同账号并发会话上限（{@code 0} 表示不限）
 * @param totpRequiredRoles    强制 TOTP 的角色编码列表
 */
public record AuthPolicyView(String passwordPolicy, int sessionIdleMinutes, int sessionMaxHours,
                             int concurrentSessions, List<String> totpRequiredRoles) {
}
