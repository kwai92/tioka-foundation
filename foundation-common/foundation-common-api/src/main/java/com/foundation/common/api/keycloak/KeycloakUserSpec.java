package com.foundation.common.api.keycloak;

import java.util.List;

/**
 * Keycloak 用户登记期望（F-04 BAS-03）。
 *
 * @param username        登录名（realm 内唯一）
 * @param email           邮箱（可空；用于自动关联与重置邮件）
 * @param firstName       名（显示名）
 * @param lastName        姓（可空）
 * @param enabled         是否可登录
 * @param requiredActions 必需动作集合，取值见 {@link KeycloakRequiredActions}
 */
public record KeycloakUserSpec(String username,
                               String email,
                               String firstName,
                               String lastName,
                               boolean enabled,
                               List<String> requiredActions) {
}
