package com.foundation.common.api.keycloak;

import java.util.List;

/**
 * Keycloak 用户管理契约（F-04 BAS-03）。
 *
 * <p>跨模块只依赖 {@code -api}：账号领域只依赖本接口，实现落在 {@code foundation-common-biz} 的 Keycloak 管理接口客户端内。
 *
 * <p>Foundation 不保存口令（F-04 §5）：密码、TOTP 由 Keycloak 保存与校验；本接口只做用户表示与生命周期动作，
 * 明文口令仅作为 {@link #resetPassword} 的入参出现，不经本接口回读、不落库、不进日志。
 */
public interface KeycloakUserAdmin {

    /** 按 {@code username} 幂等建用户并返回 Keycloak 用户 ID；同名已存在时返回既有 ID。 */
    String createUser(KeycloakUserSpec spec);

    /** 按 Keycloak 用户 ID 取用户表示；不存在返回 {@code null}。 */
    KeycloakUserView findUser(String userId);

    /** 按登录名精确取用户表示；不存在返回 {@code null}。 */
    KeycloakUserView findByUsername(String username);

    /** 列出 realm 内全部用户（每日对账用，内部翻页拉全量）。 */
    List<KeycloakUserView> listUsers();

    /** 设置是否可登录（停用 {@code enabled=false}，登录被拒）。 */
    void setEnabled(String userId, boolean enabled);

    /** 修改登录名（对账以 Foundation 为准回正时使用）。 */
    void setUsername(String userId, String username);

    /** 设置必需动作（如首次改密 {@code UPDATE_PASSWORD}、{@code CONFIGURE_TOTP}）。 */
    void setRequiredActions(String userId, List<String> requiredActions);

    /** 删除用户；不存在时静默返回。 */
    void deleteUser(String userId);

    /** 设定临时口令（明文一次性入参；{@code temporary=true} 表示首次登录须修改）。 */
    void resetPassword(String userId, String temporaryPassword, boolean temporary);

    /** 触发 Keycloak 发送重置口令邮件（口令与链接由 Keycloak 生成，Foundation 不接触）。 */
    void sendPasswordResetEmail(String userId);

    /** 吊销该用户全部会话（强制下线）。 */
    void logoutSessions(String userId);

    /** 列出该用户在线会话。 */
    List<KeycloakSessionView> listSessions(String userId);
}
