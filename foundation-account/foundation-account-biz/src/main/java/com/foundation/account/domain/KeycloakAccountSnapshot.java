package com.foundation.account.domain;

import java.util.List;

/**
 * Keycloak 侧账号对账快照（F-04 §6）。
 *
 * @param keycloakUserId  Keycloak 用户 ID
 * @param username        登录名
 * @param enabled         是否可登录
 * @param requiredActions 必需动作集合
 */
public record KeycloakAccountSnapshot(String keycloakUserId,
                                      String username,
                                      boolean enabled,
                                      List<String> requiredActions) {

    public KeycloakAccountSnapshot {
        requiredActions = requiredActions == null ? List.of() : List.copyOf(requiredActions);
    }
}
