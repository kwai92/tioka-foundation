package com.foundation.common.api.keycloak;

import java.util.List;

/**
 * Keycloak 用户表示（F-04 BAS-03 对账维度：存在性、{@code enabled}、{@code username}、必需动作）。
 *
 * @param id              Keycloak 用户 ID
 * @param username        登录名
 * @param email           邮箱
 * @param enabled         是否可登录
 * @param requiredActions 必需动作集合
 */
public record KeycloakUserView(String id,
                               String username,
                               String email,
                               boolean enabled,
                               List<String> requiredActions) {
}
