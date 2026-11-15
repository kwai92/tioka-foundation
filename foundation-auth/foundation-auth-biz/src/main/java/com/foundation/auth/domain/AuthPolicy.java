package com.foundation.auth.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录策略领域对象（F-04 BAS-15 §2：密码策略与会话策略）。
 *
 * @param id                 策略 ID（{@code pol_…}）
 * @param passwordPolicy     Keycloak passwordPolicy 字符串
 * @param sessionIdleMinutes SSO 会话空闲超时（分钟）
 * @param sessionMaxHours    SSO 会话最长有效（小时）
 * @param concurrentSessions 并发会话上限（{@code 0} 表示不限）
 * @param totpRequiredRoles  强制 TOTP 的角色编码列表
 */
public record AuthPolicy(String id, String passwordPolicy, int sessionIdleMinutes, int sessionMaxHours,
                        int concurrentSessions, List<String> totpRequiredRoles,
                        LocalDateTime createAt, LocalDateTime updateAt) {
}
