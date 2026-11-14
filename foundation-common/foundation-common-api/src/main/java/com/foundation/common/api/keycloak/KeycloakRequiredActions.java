package com.foundation.common.api.keycloak;

/** Keycloak 必需动作常量（用户管理用）。 */
public final class KeycloakRequiredActions {

    /** 首次登录必须修改口令。 */
    public static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";

    /** 配置动态口令（TOTP）。 */
    public static final String CONFIGURE_TOTP = "CONFIGURE_TOTP";

    private KeycloakRequiredActions() {
    }
}
