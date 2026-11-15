package com.foundation.common.api.keycloak;

import java.util.List;

/**
 * Keycloak realm 级配置下发契约（F-04 BAS-15：登录方式与策略下发到 realm）。
 *
 * <p>登录协议全部由 Keycloak 实现，Foundation 只做配置与管理（F-04 §1）。本接口封装三类下发：
 * 密码策略、会话策略、登录方式（身份代理启用）与必需动作（TOTP）。实现抛
 * {@link IllegalStateException} 表示该项下发失败，由调用方汇总失败项与 Keycloak 返回，**不静默成功**。
 */
public interface KeycloakRealmAdmin {

    /**
     * 下发密码策略到 realm（Keycloak {@code passwordPolicy} 字符串，
     * 例如 {@code length(8) and digits(1) and upperCase(1)}；blank 表示清空）。
     */
    void applyPasswordPolicy(String passwordPolicy);

    /**
     * 下发会话策略：SSO 会话空闲分钟数、最长小时数、同账号并发会话上限（{@code 0} 表示不限）。
     */
    void applySessionPolicy(int idleMinutes, int maxHours, int concurrentSessions);

    /**
     * 启用 / 停用某登录方式（以身份代理别名 = {@code modeCode} 定位）。
     *
     * <p>账号密码为 Keycloak 本地凭证，恒可用，不需要身份代理；调用方不对其下发。
     */
    void applyLoginMode(String modeCode, boolean enabled);

    /**
     * 下发必需动作（如 {@code CONFIGURE_TOTP}）：在 realm 上启用并设为默认动作。
     */
    void applyRequiredActions(List<String> requiredActions);
}
