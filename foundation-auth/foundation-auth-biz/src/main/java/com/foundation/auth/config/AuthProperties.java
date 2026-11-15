package com.foundation.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 登录方式配置（F-04 BAS-15 §6 配置项，前缀 {@code foundation.auth.*}）。
 *
 * <p>本节只承载策略默认值与登录方式清单：首次读取（{@code fdn_auth_policy} 无行）时以 {@link #policy} 播种。
 * Keycloak 连接（{@code base-url}/{@code realm}/{@code admin-client-id}/{@code admin-client-secret}）
 * 沿用 BAS-01 的 {@code foundation.keycloak.*}（本项不重复定义连接，避免与 BAS-01 冲突）。
 */
@ConfigurationProperties(prefix = "foundation.auth")
public class AuthProperties {

    /** 密码 / 会话策略默认值（S15 第 15 节）。 */
    private Policy policy = new Policy();

    /** 登录方式清单（默认启用全部本期方式）。 */
    private List<String> loginModes = List.of("password", "oidc", "ldap", "saml", "im-qr");

    /** 强制 TOTP 的角色（S15 第 15 节三员分立）。 */
    private List<String> totpRequiredRoles = List.of("system_admin", "security_admin");

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public List<String> getLoginModes() {
        return loginModes;
    }

    public void setLoginModes(List<String> loginModes) {
        this.loginModes = loginModes;
    }

    public List<String> getTotpRequiredRoles() {
        return totpRequiredRoles;
    }

    public void setTotpRequiredRoles(List<String> totpRequiredRoles) {
        this.totpRequiredRoles = totpRequiredRoles;
    }

    /** 密码 / 会话策略默认值。 */
    public static class Policy {

        /** 密码策略（Keycloak passwordPolicy 字符串）：最少 8 位、含数字与大写。 */
        private String password = "length(8) and digits(1) and upperCase(1)";

        /** 会话空闲超时（分钟）。 */
        private int sessionIdleMinutes = 30;

        /** 会话最长有效（小时）。 */
        private int sessionMaxHours = 10;

        /** 并发会话上限（{@code 0} 表示不限）。 */
        private int concurrentSessions = 0;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getSessionIdleMinutes() {
            return sessionIdleMinutes;
        }

        public void setSessionIdleMinutes(int sessionIdleMinutes) {
            this.sessionIdleMinutes = sessionIdleMinutes;
        }

        public int getSessionMaxHours() {
            return sessionMaxHours;
        }

        public void setSessionMaxHours(int sessionMaxHours) {
            this.sessionMaxHours = sessionMaxHours;
        }

        public int getConcurrentSessions() {
            return concurrentSessions;
        }

        public void setConcurrentSessions(int concurrentSessions) {
            this.concurrentSessions = concurrentSessions;
        }
    }
}
